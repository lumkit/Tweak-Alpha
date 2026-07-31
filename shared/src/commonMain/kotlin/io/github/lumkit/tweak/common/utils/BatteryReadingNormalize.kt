package io.github.lumkit.tweak.common.utils

/**
 * 电流单位换算（对齐 BatteryRecorder）。
 */
object BatteryReadingNormalize {

    /** µA → mA（向 0 取整，与 Android/BR 口径一致）。 */
    fun microAmpToMilliAmp(raw: Long): Int {
        if (raw <= 0L) return raw.toInt()
        val uv = if (raw >= 100_000L) raw else raw * 1_000L
        return (uv / 1000L).toInt()
    }
}
