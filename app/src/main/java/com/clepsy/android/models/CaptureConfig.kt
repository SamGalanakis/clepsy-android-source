package com.clepsy.android.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

/**
 * Specifies the capture cadence and cooldown configuration optimized for mobile usage patterns.
 * 
 * Mobile-specific tuning:
 * - Longer same-app cooldown (users stay in apps longer on mobile)
 * - Extended heartbeat interval (reduces event spam)
 * - Slower poll interval (better battery life)
 */
data class CaptureConfig(
    val eventInterval: Duration = 30.seconds,
    val afkTimeout: Duration = 5.minutes,
    val globalCooldown: Duration = 5.seconds,
    val sameAppCooldown: Duration = 45.seconds,
    val constantAppHeartbeat: Duration = 90.seconds,
    val appPollInterval: Duration = 500.milliseconds
)
