package com.clepsy.android.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

/**
 * Specifies the capture cadence and cooldown configuration mirroring the desktop client.
 */
data class CaptureConfig(
    val eventInterval: Duration = 30.seconds,
    val afkTimeout: Duration = 5.minutes,
    val globalCooldown: Duration = 5.seconds,
    val sameAppCooldown: Duration = 15.seconds,
    val constantAppHeartbeat: Duration = 30.seconds,
    val appPollInterval: Duration = 200.milliseconds
)
