package com.clepsy.android.monitoring

import com.clepsy.android.models.AppInfo
import com.clepsy.android.models.CaptureConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Decides when the next screenshot capture should run based on cooldown rules.
 */
class CaptureScheduler(
    private val config: CaptureConfig,
    private val clock: Clock = Clock.System
) {
    private var lastCaptureTime: Instant? = null
    private var lastCapturedApp: String? = null

    fun evaluate(appInfo: AppInfo, now: Instant = clock.now()): Decision {
        val lastTime = lastCaptureTime
        val elapsedSinceCapture = lastTime?.let { now - it }
        val isNewApp = lastCapturedApp?.let { it != appInfo.packageName } ?: true

        if (lastTime == null) {
            markCapture(appInfo.packageName, now)
            return Decision.Capture(Reason.INITIAL)
        }

        if (elapsedSinceCapture != null && elapsedSinceCapture < config.globalCooldown) {
            return Decision.Skip
        }

        if (isNewApp) {
            markCapture(appInfo.packageName, now)
            return Decision.Capture(Reason.APP_SWITCH)
        }

        if (elapsedSinceCapture != null && elapsedSinceCapture >= config.sameAppCooldown) {
            markCapture(appInfo.packageName, now)
            return Decision.Capture(Reason.SAME_APP)
        }

        if (elapsedSinceCapture != null && elapsedSinceCapture >= config.constantAppHeartbeat) {
            markCapture(appInfo.packageName, now)
            return Decision.Capture(Reason.HEARTBEAT)
        }

        return Decision.Skip
    }

    fun reset() {
        lastCaptureTime = null
        lastCapturedApp = null
    }

    fun lastCaptureAge(now: Instant = clock.now()): Duration? = lastCaptureTime?.let { now - it }

    private fun markCapture(packageName: String, time: Instant) {
        lastCapturedApp = packageName
        lastCaptureTime = time
    }

    sealed class Decision {
        data class Capture(val reason: Reason) : Decision()
        data object Skip : Decision()
    }

    enum class Reason {
        INITIAL,
        APP_SWITCH,
        SAME_APP,
        HEARTBEAT
    }
}
