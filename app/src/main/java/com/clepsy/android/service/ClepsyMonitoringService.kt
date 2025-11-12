package com.clepsy.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.clepsy.android.ClepsyApp
import com.clepsy.android.MainActivity
import com.clepsy.android.R
import com.clepsy.android.config.ConfigRepository
import com.clepsy.android.models.CaptureConfig
import com.clepsy.android.models.MobileAppUsageEvent
import com.clepsy.android.models.UserConfig
import com.clepsy.android.monitoring.CaptureScheduler
import com.clepsy.android.monitoring.HeartbeatScheduler
import com.clepsy.android.monitoring.MonitoringState
import com.clepsy.android.notifications.NotificationRepository
import com.clepsy.android.network.ApiResult
import com.clepsy.android.network.ClepsyApi
import com.clepsy.android.util.DeviceInfoProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.math.max

class ClepsyMonitoringService : LifecycleService() {

    private val appGraph by lazy { (application as ClepsyApp).graph }
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var configJob: Job? = null
    private var monitoringJob: Job? = null
    private var stateObserverJob: Job? = null
    private var currentConfig: UserConfig? = null

    private val captureConfig: CaptureConfig
        get() = appGraph.captureConfig

    private val configRepository: ConfigRepository
        get() = appGraph.configRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeMonitoringState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> requestToggleActive(false)
            ACTION_RESUME -> requestToggleActive(true)
            ACTION_STOP -> {
                requestToggleActive(false)
                serviceScope.launch { stopMonitoringSession() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> ensureConfigCollection()
        }

        val notification = buildNotification(appGraph.monitoringState.value, currentConfig)
        startForeground(NOTIFICATION_ID, notification)
        ensureConfigCollection()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.launch { stopMonitoringSession() }
        configJob?.cancel()
        stateObserverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureConfigCollection() {
        if (configJob?.isActive == true) return
        configJob = serviceScope.launch {
            configRepository.config.collectLatest { config ->
                currentConfig = config
                if (!config.isPaired) {
                    stopMonitoringSession()
                    updateState { it.copy(serviceRunning = false) }
                } else if (config.active) {
                    startMonitoringSession(config)
                } else {
                    stopMonitoringSession()
                    updateState { it.copy(serviceRunning = false) }
                }
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(appGraph.monitoringState.value, currentConfig)
                )
            }
        }
    }

    private suspend fun startMonitoringSession(config: UserConfig) {
        monitoringJob?.cancelAndJoin()
        val backend = config.clepsyBackendUrl.trim().trimEnd('/')
        val deviceToken = config.deviceToken
        if (backend.isEmpty() || deviceToken.isBlank()) {
            updateState {
                it.copy(
                    serviceRunning = false,
                    lastError = "Backend or device token missing"
                )
            }
            return
        }

        val api = appGraph.createClepsyApi(backend, appGraph.okHttpClient)
        val captureScheduler = appGraph.createCaptureScheduler()
        val heartbeatScheduler = appGraph.createHeartbeatScheduler()
        val detector = appGraph.createForegroundAppDetector()
        val deviceInfoProvider = appGraph.createDeviceInfoProvider()
        val notificationRepository: NotificationRepository = appGraph.notificationRepository
        val pollDelayMillis = max(50L, captureConfig.appPollInterval.inWholeMilliseconds)
        var lastForegroundPackage: String? = null
        var screenWasInteractive = false

        monitoringJob = serviceScope.launch {
            updateState { it.copy(serviceRunning = true, lastError = null) }

            val initialHeartbeatError = sendHeartbeat(api, deviceToken)
            if (initialHeartbeatError != null) {
                updateState { it.copy(lastError = initialHeartbeatError) }
            } else {
                val heartbeatTime = Clock.System.now()
                heartbeatScheduler.markSent(heartbeatTime)
                updateState { it.copy(lastHeartbeat = heartbeatTime, lastError = null) }
            }

            try {
                loop@ while (isActive) {
                    val now = Clock.System.now()
                    val deviceInfo = deviceInfoProvider.collect()
                    if (!deviceInfo.screenOn) {
                        captureScheduler.reset()
                        heartbeatScheduler.reset()
                        if (screenWasInteractive) {
                            screenWasInteractive = false
                            lastForegroundPackage = null
                        }
                        delay(1_000)
                        continue
                    }

                    screenWasInteractive = true

                    val foreground = detector.currentForegroundApp()
                    if (foreground != null) {
                        if (foreground.packageName != lastForegroundPackage) {
                            lastForegroundPackage = foreground.packageName
                        }
                        
                        // Check filter BEFORE scheduler evaluation to avoid affecting cooldowns
                        if (config.shouldFilterApp(foreground.packageName, foreground.appLabel)) {
                            // Event filtered - skip completely without affecting scheduler state
                            delay(pollDelayMillis)
                            continue@loop
                        }
                        
                        when (val decision = captureScheduler.evaluate(foreground, now)) {
                            is CaptureScheduler.Decision.Capture -> {
                                val snapshot = notificationRepository.snapshotFor(foreground.packageName)
                                val event = MobileAppUsageEvent(
                                    packageName = foreground.packageName,
                                    appLabel = foreground.appLabel,
                                    timestamp = now,
                                    activityName = foreground.activityName,
                                    mediaMetadata = snapshot?.mediaMetadata,
                                    notificationText = snapshot?.notificationText
                                )

                                when (val result = api.sendAppUsageEvent(deviceToken, event)) {
                                    is ApiResult.Success -> {
                                        updateState {
                                            it.copy(
                                                lastEvent = now,
                                                lastEventReason = decision.reason,
                                                lastError = null
                                            )
                                        }
                                    }
                                    is ApiResult.Failure -> {
                                        captureScheduler.reset()
                                        val message = result.message?.takeIf { msg -> msg.isNotBlank() }
                                            ?: "Usage event upload failed"
                                        updateState { it.copy(lastError = message) }
                                    }
                                }
                            }
                            CaptureScheduler.Decision.Skip -> Unit
                        }
                    }

                    if (heartbeatScheduler.shouldSend(now)) {
                        val result = sendHeartbeat(api, deviceToken)
                        heartbeatScheduler.markSent(now)
                        if (result != null) {
                            updateState { it.copy(lastError = result) }
                        } else {
                            updateState { it.copy(lastHeartbeat = now, lastError = null) }
                        }
                    }

                    delay(pollDelayMillis)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                updateState { it.copy(lastError = t.message ?: "Monitoring error") }
            } finally {
                captureScheduler.reset()
                heartbeatScheduler.reset()
            }
        }
    }

    private suspend fun stopMonitoringSession() {
        monitoringJob?.cancelAndJoin()
        monitoringJob = null
        updateState { it.copy(serviceRunning = false) }
    }

    private suspend fun sendHeartbeat(api: ClepsyApi, deviceToken: String): String? {
        return when (val result = api.sendHeartbeat(deviceToken)) {
            is ApiResult.Success -> null
            is ApiResult.Failure -> result.message ?: "Heartbeat failed"
        }
    }

    private fun updateState(block: (MonitoringState) -> MonitoringState) {
        appGraph.monitoringState.update(block)
    }

    private fun requestToggleActive(active: Boolean) {
        val config = currentConfig
        if (config == null || config.active == active) return
        serviceScope.launch {
            configRepository.toggleActive(active)
        }
    }

    private fun observeMonitoringState() {
        stateObserverJob = serviceScope.launch {
            appGraph.monitoringState.collect { state ->
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(state, currentConfig)
                )
            }
        }
    }

    private fun buildNotification(state: MonitoringState, config: UserConfig?): Notification {
        // Intent to open app - does NOT change monitoring state
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = getString(R.string.notification_monitoring_title)
        val summary = when {
            config == null || !config.isPaired -> getString(R.string.notification_status_unpaired)
            config.active && state.serviceRunning -> getString(R.string.notification_status_running)
            config.active && !state.serviceRunning -> getString(R.string.notification_status_starting)
            else -> getString(R.string.notification_status_paused)
        }

        val details = buildNotificationDetails(state)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(contentIntent)
            .setAutoCancel(false)  // Don't dismiss on click
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (config?.isPaired == true) {
            if (config.active) {
                builder.addAction(
                    0,
                    getString(R.string.notification_action_pause),
                    servicePendingIntent(ACTION_PAUSE)
                )
            } else {
                builder.addAction(
                    0,
                    getString(R.string.notification_action_resume),
                    servicePendingIntent(ACTION_RESUME)
                )
            }
            builder.addAction(
                0,
                getString(R.string.notification_action_stop),
                servicePendingIntent(ACTION_STOP)
            )
        }

        return builder.build()
    }

    private fun buildNotificationDetails(state: MonitoringState): String {
    val captureText = state.lastEvent?.let { "Last event: ${formatInstant(it)}" }
        val heartbeatText = state.lastHeartbeat?.let { "Last heartbeat: ${formatInstant(it)}" }
    val errorText = state.lastError?.let { "Error: $it" }
        return listOfNotNull(captureText, heartbeatText, errorText)
            .ifEmpty { listOf(getString(R.string.notification_detail_idle)) }
            .joinToString(separator = "\n")
    }

    private fun formatInstant(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = local.time.toString().take(8)
        return "${local.date} $time"
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, ClepsyMonitoringService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.notification_channel_description)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "clepsy_monitoring"
        private const val NOTIFICATION_ID = 1001

        internal const val ACTION_START = "com.clepsy.android.service.action.START"
        internal const val ACTION_PAUSE = "com.clepsy.android.service.action.PAUSE"
        internal const val ACTION_RESUME = "com.clepsy.android.service.action.RESUME"
        internal const val ACTION_STOP = "com.clepsy.android.service.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ClepsyMonitoringService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, ClepsyMonitoringService::class.java).setAction(ACTION_PAUSE)
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, ClepsyMonitoringService::class.java).setAction(ACTION_RESUME)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClepsyMonitoringService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
