package com.clepsy.android.monitoring

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Tracks the most recent interaction timestamp to support AFK detection logic.
 */
class UserInteractionTracker(
    private val clock: Clock = Clock.System
) {
    private var lastInteraction: Instant = clock.now()

    fun markInteraction(now: Instant = clock.now()) {
        lastInteraction = now
    }

    fun timeSinceLastInteraction(now: Instant = clock.now()): Duration = now - lastInteraction

    fun reset(now: Instant = clock.now()) {
        lastInteraction = now
    }
}
