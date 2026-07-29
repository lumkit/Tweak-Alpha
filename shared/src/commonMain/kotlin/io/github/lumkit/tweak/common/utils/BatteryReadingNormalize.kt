package io.github.lumkit.tweak.common.utils

import kotlin.math.abs

/**
 * 电池电流读数归一化（App UI 与 tweak_server 采样共用）。
 *
 * 瞬时电流对齐 [BatteryRecorder](https://github.com/Itosang/BatteryRecorder)：
 * 仅 `CURRENT_NOW` / `current_now`，内核单位为 µA；不使用 `CONSTANT_CHARGE_CURRENT`。
 */
object BatteryReadingNormalize {

    /** BatteryManager / `batteryproperties` 返回 µA；少数 OEM 直接回 mA。 */
    fun platformCurrentToMa(rawUaOrMa: Long): Int {
        return kernelCurrentNowToMa(rawUaOrMa)
    }

    /**
     * `power_supply` 的 `current_now`：规范为 µA（见 Linux power_supply 文档）。
     * 与 BatteryRecorder JNI `nativeGetCurrent()` 一致，再换算为 mA。
     */
    fun kernelCurrentNowToMa(rawMicroAmps: Long): Int {
        return if (abs(rawMicroAmps) < 10_000L) {
            rawMicroAmps.toInt()
        } else {
            (rawMicroAmps / 1000L).toInt()
        }
    }

    fun coercePlausibleCurrentMa(ma: Int): Int? {
        if (ma == Int.MIN_VALUE) return null
        return ma
    }

    fun capacityScaleDigitLength(vararg rawCapacity: Long?): Int {
        return rawCapacity.filterNotNull().filter { it > 0L }.maxOfOrNull { it.toString().length } ?: 0
    }
}
