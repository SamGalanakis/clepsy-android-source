package com.clepsy.android.models

import android.graphics.Rect
import kotlinx.datetime.Instant

/**
 * Represents the foreground application context at the time of a capture.
 */
data class AppInfo(
    val packageName: String,
    val activityName: String?,
    val appLabel: String,
    val timestamp: Instant,
    val screenBounds: Rect,
    val isForeground: Boolean = true
)
