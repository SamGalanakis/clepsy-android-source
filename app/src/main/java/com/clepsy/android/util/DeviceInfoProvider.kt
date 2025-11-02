package com.clepsy.android.util

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.clepsy.android.models.DeviceInfo

class DeviceInfoProvider(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val batteryManager: BatteryManager? = appContext.getSystemService()
    private val powerManager: PowerManager? = appContext.getSystemService()

    fun collect(): DeviceInfo {
        val orientation = context.resources.configuration.orientation
        return DeviceInfo(
            screenOn = powerManager?.isInteractive ?: true,
            batteryLevel = readBatteryLevel(),
            orientation = DeviceInfo.Orientation.fromConfiguration(orientation)
        )
    }

    private fun readBatteryLevel(): Int {
        val manager = batteryManager ?: return UNKNOWN_BATTERY_LEVEL
        val property = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            UNKNOWN_BATTERY_LEVEL
        }
        return if (property in 0..100) property else UNKNOWN_BATTERY_LEVEL
    }

    companion object {
        private const val UNKNOWN_BATTERY_LEVEL = -1
    }
}
