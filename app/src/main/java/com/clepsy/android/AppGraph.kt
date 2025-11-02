package com.clepsy.android

import android.content.Context
import com.clepsy.android.config.ConfigRepository
import com.clepsy.android.models.CaptureConfig
import com.clepsy.android.monitoring.CaptureScheduler
import com.clepsy.android.monitoring.ForegroundAppDetector
import com.clepsy.android.monitoring.HeartbeatScheduler
import com.clepsy.android.monitoring.MonitoringState
import com.clepsy.android.monitoring.UserInteractionTracker
import com.clepsy.android.notifications.NotificationRepository
import com.clepsy.android.network.ClepsyApi
import com.clepsy.android.network.HttpClepsyApi
import com.clepsy.android.storage.UserConfigStorage
import com.clepsy.android.util.DeviceInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Simple dependency container for the application scope.
 */
class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    val userConfigStorage: UserConfigStorage by lazy {
        UserConfigStorage(appContext)
    }

    val configRepository: ConfigRepository by lazy {
        ConfigRepository(userConfigStorage, okHttpClient) { baseUrl, client ->
            HttpClepsyApi(baseUrl, client, json)
        }
    }

    val captureConfig: CaptureConfig = CaptureConfig()

    val monitoringState: MutableStateFlow<MonitoringState> = MutableStateFlow(MonitoringState())

    val notificationRepository: NotificationRepository by lazy {
        NotificationRepository()
    }

    fun createCaptureScheduler(): CaptureScheduler = CaptureScheduler(captureConfig)

    fun createHeartbeatScheduler(): HeartbeatScheduler = HeartbeatScheduler()

    fun createForegroundAppDetector(): ForegroundAppDetector = ForegroundAppDetector(appContext)

    fun createUserInteractionTracker(): UserInteractionTracker = UserInteractionTracker()

    fun createDeviceInfoProvider(): DeviceInfoProvider = DeviceInfoProvider(appContext)

    fun createClepsyApi(baseUrl: String, client: OkHttpClient = okHttpClient): ClepsyApi =
        HttpClepsyApi(baseUrl, client, json)
}
