package io.github.lumkit.tweak.ui.screen.dischargeStatistics

/**
 * 理论续航估算输入。
 *
 * @param designCapacityMah 设计容量 mAh；无效时跳过 A
 * @param avgPowerUw 平均功率 µW；≤0 时跳过 A
 * @param durationMs 本段已用时长
 * @param startLevel 段初电量 %
 * @param endLevel 段末电量 %
 * @param remainingLevel 当前剩余电量 %（用于 C）
 */
data class DischargeEtaInput(
    val designCapacityMah: Int?,
    val avgPowerUw: Long,
    val durationMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val remainingLevel: Int,
)

/**
 * 理论续航（毫秒）：A → B → C 逐步降级，全失败返回 null。
 *
 * - A: `设计容量Wh(3.7V) / 平均功耗W`
 * - B: `(时长 / 掉电量%) × 100%`
 * - C: `(时长 / 掉电量%) × 剩余%`
 */
fun estimateDischargeEtaMs(input: DischargeEtaInput): Long? {
    val avgW = input.avgPowerUw / 1_000_000.0
    val designMah = input.designCapacityMah
    if (designMah != null && designMah > 0 && avgW > 0.0) {
        val designWh = designMah / 1000.0 * 3.7
        return (designWh / avgW * 3_600_000.0).toLong().takeIf { it > 0L }
    }
    val dropped = input.startLevel - input.endLevel
    if (input.durationMs > 0L && dropped > 0) {
        return (input.durationMs.toDouble() / dropped * 100.0).toLong().takeIf { it > 0L }
    }
    if (input.durationMs > 0L && dropped > 0 && input.remainingLevel > 0) {
        return (input.durationMs.toDouble() / dropped * input.remainingLevel)
            .toLong()
            .takeIf { it > 0L }
    }
    return null
}
