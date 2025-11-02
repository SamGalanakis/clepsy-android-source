package com.clepsy.android.models

import android.content.res.Configuration

/**
 * Snapshot of device state that's included alongside capture metadata.
 */
data class DeviceInfo(
    val screenOn: Boolean,
    val batteryLevel: Int,
    val orientation: Orientation
) {
    enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
        UNKNOWN;

        companion object {
            fun fromConfiguration(orientation: Int): Orientation = when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> LANDSCAPE
                Configuration.ORIENTATION_PORTRAIT -> PORTRAIT
                else -> UNKNOWN
            }
        }
    }
}
