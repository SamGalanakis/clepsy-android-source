package com.clepsy.android.monitoring

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Controls when heartbeats should be dispatched to the Clepsy backend.
 */
class HeartbeatScheduler(
    private val minInterval: Duration = 30.seconds,
    private val maxInterval: Duration = 60.seconds,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default
) {
    private var nextHeartbeat: Instant? = null

    fun shouldSend(now: Instant = clock.now()): Boolean {
        val scheduled = nextHeartbeat ?: run {
            scheduleNext(now)
            nextHeartbeat
        }
        return scheduled?.let { now >= it } ?: false
    }

    fun markSent(now: Instant = clock.now()) {
        scheduleNext(now)
    }

    fun reset() {
        nextHeartbeat = null
    }

    private fun scheduleNext(from: Instant) {
        val minMillis = minInterval.inWholeMilliseconds
        val maxMillis = maxInterval.inWholeMilliseconds
        val jitterMillis = if (maxMillis > minMillis) {
            random.nextLong(minMillis, maxMillis + 1)
        } else {
            minMillis
        }
        nextHeartbeat = from + jitterMillis.milliseconds
    }
}