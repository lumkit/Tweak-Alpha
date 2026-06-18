package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.sharednative.BatteryBridge

internal actual object PlatformBatterySource {
    actual fun getCurrentNow(): Long? {
        val value = BatteryBridge.getCurrentNow()
        if (value == Int.MIN_VALUE) return null
        return value.toLong()
    }
}
