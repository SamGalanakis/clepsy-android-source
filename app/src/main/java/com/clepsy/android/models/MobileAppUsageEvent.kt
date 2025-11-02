package com.clepsy.android.models

import kotlinx.datetime.Instant

/**
 * Represents a usage observation for the current foreground application.
 */
data class MobileAppUsageEvent(
    val packageName: String,
    val appLabel: String,
    val timestamp: Instant,
    val activityName: String? = null,
    val mediaMetadata: Map<String, String>? = null,
    val notificationText: String? = null
)
