package io.github.lumkit.tweak.ui.screen.dischargeStatistics

/**
 * 剩余续航估算输入（到 0%）。
 *
 * @param capacityMah 满充容量优先，否则设计容量；无效时跳过能量路径
 * @param avgPowerUw 平均功率 µW；≤0 时跳过能量路径
 * @param avgVoltageMv 会话平均电压 mV；无效时能量路径用 3.7V
 * @param durationMs 本段已用时长
 * @param startLevel 段初电量 %
 * @param endLevel 段末电量 %
 * @param remainingLevel 当前剩余电量 %
 */
data class DischargeEtaInput(
    val capacityMah: Int?,
    val avgPowerUw: Long,
    val avgVoltageMv: Int?,
    val durationMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val remainingLevel: Int,
)

private const val MIN_DROP_LEVEL = 2
private const val MIN_DURATION_MS = 3 * 60_000L
private const val DEFAULT_VOLTAGE_V = 3.7

/**
 * 剩余续航（毫秒）：S（SoC 斜率）→ E（剩余能量/功率），全失败返回 null。
 *
 * - S: `(时长 / 掉电量%) × 剩余%`（掉电 ≥2% 且时长 ≥3min）
 * - E: `remainingWh / avgW`（S 未启用时）
 */
fun estimateDischargeEtaMs(input: DischargeEtaInput): Long? {
    val remaining = input.remainingLevel.coerceAtLeast(0)
    if (remaining <= 0) return null

    val dropped = input.startLevel - input.endLevel
    if (dropped >= MIN_DROP_LEVEL && input.durationMs >= MIN_DURATION_MS) {
        return (input.durationMs.toDouble() / dropped * remaining)
            .toLong()
            .takeIf { it > 0L }
    }

    val avgW = input.avgPowerUw / 1_000_000.0
    val cap = input.capacityMah
    if (cap != null && cap > 0 && avgW > 0.0) {
        val v = input.avgVoltageMv
            ?.takeIf { it > 0 }
            ?.div(1000.0)
            ?: DEFAULT_VOLTAGE_V
        val remainingWh = cap / 1000.0 * v * remaining / 100.0
        return (remainingWh / avgW * 3_600_000.0).toLong().takeIf { it > 0L }
    }
    return null
}
