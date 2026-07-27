package io.github.lumkit.tweak.common.database.battery

/**
 * 会话功率汇总（由 SQL 聚合，避免把全量 sample 拉进内存）。
 *
 * [powerSumUw]：Σ |mA × mV|（µW）
 * [sampleCount]：参与统计的有效采样数（电流、电压均非空）
 */
data class BatteryPowerAggregate(
    val powerSumUw: Long = 0L,
    val sampleCount: Long = 0L,
) {
    val averagePowerUw: Long
        get() = if (sampleCount <= 0L) 0L else powerSumUw / sampleCount
}
