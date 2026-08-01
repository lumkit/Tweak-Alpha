package io.github.lumkit.tweak.common.utils

/**
 * 电池读数单位换算 + 用户校准（电流）。
 *
 * 电流：按 [CurrentCalibration.scale] 缩放，可选双电芯 ×2。
 * 电压：仅 µV→mV，不受电流校准影响。
 */
object BatteryReadingNormalize {

    data class CurrentCalibration(
        val dualCell: Boolean = false,
        val scale: Long = DEFAULT_SCALE,
    )

    /** 离散数量级（跳过 0），Slider 按索引选取。 */
    val SCALE_STEPS: List<Long> = listOf(
        -1_000_000L, -100_000L, -10_000L, -1_000L, -100L, -10L, -1L,
        1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L,
    )

    const val DEFAULT_SCALE: Long = -1_000L

    @Volatile
    private var calibration: CurrentCalibration = CurrentCalibration()

    fun currentCalibration(): CurrentCalibration = calibration

    fun updateCalibration(next: CurrentCalibration) {
        calibration = next.copy(scale = coerceScale(next.scale))
    }

    fun coerceScale(scale: Long): Long {
        if (scale == 0L) return DEFAULT_SCALE
        val nearest = SCALE_STEPS.minByOrNull { kotlin.math.abs(it - scale) } ?: DEFAULT_SCALE
        return nearest
    }

    fun scaleIndex(scale: Long): Int {
        val coerced = coerceScale(scale)
        val idx = SCALE_STEPS.indexOf(coerced)
        return if (idx >= 0) idx else SCALE_STEPS.indexOf(DEFAULT_SCALE).coerceAtLeast(0)
    }

    fun scaleAtIndex(index: Int): Long {
        return SCALE_STEPS.getOrElse(index.coerceIn(SCALE_STEPS.indices)) { DEFAULT_SCALE }
    }

    /**
     * 原始电流 → mA。
     * `scale < 0`：`raw / |scale|`；`scale > 0`：`raw * scale`；再按双电芯 ×2。
     */
    fun normalizeCurrent(raw: Long): Int {
        val cfg = calibration
        val scale = coerceScale(cfg.scale)
        var scaled = raw / -scale
        if (cfg.dualCell) {
            scaled *= 2L
        }
        return scaled.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    /** 原始电压（µV）→ mV。 */
    fun normalizeVoltage(raw: Long): Int {
        val mv = if (kotlin.math.abs(raw) >= 1_000L) raw / 1_000L else raw
        return mv.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }
}
