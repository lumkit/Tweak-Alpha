package io.github.lumkit.tweak.ui.screen.chargeStatistics

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlin.math.abs

internal object ChargeSessionChartsMapper {

    fun buildSummary(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
    ): ChargeStatisticsViewModel.ChargingSessionSummary? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val isOngoingCharging =
            session.chargeState == BatteryChargeState.CHARGING && session.endedAt == null
        return ChargeStatisticsViewModel.ChargingSessionSummary(
            startedAt = session.startedAt,
            endedAt = session.endedAt ?: last.timestamp,
            isCurrentChargingSession = isOngoingCharging,
            startLevel = first.level,
            endLevel = last.level,
            energyGainUw = calculateEnergyGainUw(samples),
        )
    }

    fun buildChartSamples(
        samples: List<BatteryRecordSampleEntity>,
    ): List<ChargeStatisticsViewModel.ChargeChartSample> {
        if (samples.isEmpty()) return emptyList()
        val startAt = samples.first().timestamp
        return samples.map { sample ->
            val powerUw = samplePowerUw(sample)
            ChargeStatisticsViewModel.ChargeChartSample(
                elapsedMs = (sample.timestamp - startAt).coerceAtLeast(0L),
                powerW = powerUw?.let { it / 1_000_000f } ?: 0f,
                currentMa = sample.currentMa?.let { abs(it).toFloat() } ?: 0f,
                level = sample.level.toFloat(),
                temperatureC = sample.temperatureC ?: 0f,
            )
        }
    }

    fun calculateEnergyGainUw(samples: List<BatteryRecordSampleEntity>): Long {
        if (samples.size < 2) return 0L
        var total = 0.0
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            val previousCurrent = previous.currentMa
            val previousVoltage = previous.voltageMv
            if (previousCurrent == null || previousVoltage == null) continue
            val durationHours =
                (current.timestamp - previous.timestamp).coerceAtLeast(0L) / 3_600_000.0
            val powerW = abs(previousCurrent.toDouble() * previousVoltage.toDouble()) / 1_000_000.0
            total += powerW * durationHours
        }
        return (total * 1_000_000.0).toLong()
    }

    private fun samplePowerUw(sample: BatteryRecordSampleEntity): Long? {
        val currentMa = sample.currentMa ?: return null
        val voltageMv = sample.voltageMv ?: return null
        return abs(currentMa.toLong() * voltageMv.toLong())
    }
}
