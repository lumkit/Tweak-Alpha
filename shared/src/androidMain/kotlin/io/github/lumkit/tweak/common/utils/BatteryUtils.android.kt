package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.sharednative.BatteryBridge

internal actual object PlatformBatterySource {
    actual fun getCurrentNow(): Long? {
        val value = BatteryBridge.getCurrentNow()
        if (value == Int.MIN_VALUE || value == 0) return null
        return value.toLong()
    }

    actual fun getVoltage(): Int? {
        val value = BatteryBridge.getVoltage()
        if (value == Int.MIN_VALUE) return null
        return value
    }

    actual fun getTemperature(): Int? {
        val value = BatteryBridge.getTemperature()
        if (value == Int.MIN_VALUE) return null
        return value
    }

    actual fun getCapacity(): Int? {
        val value = BatteryBridge.getCapacity()
        if (value == Int.MIN_VALUE) return null
        return value
    }
}
