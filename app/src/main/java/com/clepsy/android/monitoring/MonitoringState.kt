package com.clepsy.android.monitoring

import kotlinx.datetime.Instant

/**
 * Aggregates monitoring telemetry for UI display.
 */
data class MonitoringState(
    val serviceRunning: Boolean = false,
    val lastEvent: Instant? = null,
    val lastHeartbeat: Instant? = null,
    val lastEventReason: CaptureScheduler.Reason? = null,
    val lastError: String? = null
)
