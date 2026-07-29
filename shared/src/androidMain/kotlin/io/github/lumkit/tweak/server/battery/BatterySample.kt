package io.github.lumkit.tweak.server.battery

/**
 * 单次电池采样（已归一到 App/Room 口径）。
 * [voltageMv]/[currentMa]、[tempCenti] 使用 Int/Short 空值哨兵与 v2 日志一致。
 */
data class BatterySample(
    val timestampMs: Long,
    val level: Int,
    val voltageMv: Int = Int.MIN_VALUE,
    val currentMa: Int = Int.MIN_VALUE,
    val tempCenti: Short = Short.MIN_VALUE,
    val screenOn: Boolean = false,
    /** 0 放电 / 1 充电 */
    val state: Int = 0,
) {
    val hasValidLevel: Boolean get() = level in 0..100
}
