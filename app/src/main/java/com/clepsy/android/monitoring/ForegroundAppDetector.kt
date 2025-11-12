package com.clepsy.android.monitoring

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.content.getSystemService
import com.clepsy.android.models.AppInfo
import kotlinx.datetime.Clock

/**
 * Determines the current foreground application using UsageStats APIs.
 */
class ForegroundAppDetector(
    context: Context,
    private val clock: Clock = Clock.System
) {
    private val appContext = context.applicationContext
    private val usageStatsManager: UsageStatsManager? = appContext.getSystemService()
    private val activityManager: ActivityManager? = appContext.getSystemService()
    private val packageManager: PackageManager = appContext.packageManager
    private val displayMetrics: DisplayMetrics = appContext.resources.displayMetrics

    fun currentForegroundApp(): AppInfo? {
        val usageManager = usageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val start = now - QUERY_WINDOW_MILLIS

        val latest = usageManager.queryEvents(start, now).useEvents { events ->
            var lastPackage: String? = null
            var lastClass: String? = null
            var lastTimestamp = 0L
            val event = UsageEvents.Event()
            while (events.getNextEvent(event)) {
                val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (isForegroundEvent && event.timeStamp >= lastTimestamp) {
                    lastPackage = event.packageName
                    lastClass = event.className
                    lastTimestamp = event.timeStamp
                }
            }
            ForegroundApp(lastPackage, lastClass)
        }

        val packageName = latest.packageName ?: fallbackPackageName() ?: return null
        val activityName = latest.activityName
        val appLabel = resolveAppLabel(packageName)
        val timestamp = clock.now()
        val bounds = Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)

        return AppInfo(
            packageName = packageName,
            activityName = activityName,
            appLabel = appLabel,
            timestamp = timestamp,
            screenBounds = bounds,
            isForeground = true
        )
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo)?.toString() ?: packageName
        } catch (notFound: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun fallbackPackageName(): String? {
        val manager = activityManager ?: return null
        val processes = manager.runningAppProcesses ?: return null
        val foregroundProcess = processes.firstOrNull {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        return foregroundProcess?.processName
    }

    private inline fun <T> UsageEvents.useEvents(block: (UsageEvents) -> T): T {
        return try {
            block(this)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (this as? AutoCloseable)?.close()
            }
        }
    }

    companion object {
        private const val QUERY_WINDOW_MILLIS = 2_000L
    }

    private data class ForegroundApp(
        val packageName: String?,
        val activityName: String?
    )
}
