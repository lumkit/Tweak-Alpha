package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import io.github.lumkit.tweak.common.component.AppStripSegment
import io.github.lumkit.tweak.common.component.LineChartData
import io.github.lumkit.tweak.common.component.LineChartXAxisData
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.utils.AppsHelper
import io.github.lumkit.tweak.common.utils.formatElapsedTime
import io.github.lumkit.tweak.ui.screen.chargeStatistics.ChargeSessionChartsMapper
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

internal object DischargeSessionMapper {

    fun buildSummary(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
        nowMs: Long,
    ): DischargeStatisticsViewModel.DischargeSessionSummary? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val isOngoing =
            session.chargeState == BatteryChargeState.DISCHARGING && session.endedAt == null
        val endedAt = session.endedAt ?: if (isOngoing) nowMs else last.timestamp
        val energyUw = ChargeSessionChartsMapper.calculateEnergyGainUw(samples)
        val avgPowerUw = averagePowerUw(samples)
        return DischargeStatisticsViewModel.DischargeSessionSummary(
            startedAt = session.startedAt,
            endedAt = endedAt,
            isCurrentDischargingSession = isOngoing,
            startLevel = first.level,
            endLevel = last.level,
            energyUw = energyUw,
            averagePowerUw = avgPowerUw,
        )
    }

    fun buildLevelChart(
        samples: List<BatteryRecordSampleEntity>,
        seriesName: String,
        color: Color,
    ): Pair<LineChartXAxisData, LineChartData>? {
        if (samples.isEmpty()) return null
        val startAt = samples.first().timestamp
        val xAxis = LineChartXAxisData(
            dataSet = samples.map { (it.timestamp - startAt).coerceAtLeast(0L).formatElapsedTime() },
        )
        // 固定观感接近 0–100：在序列中保留真实电量值，坐标轴由 SmoothLineChart 按最大值取整
        val series = LineChartData(
            name = seriesName,
            suffix = "%",
            dataSet = samples.map { it.level.toFloat() },
            color = color,
        )
        return xAxis to series
    }

    fun buildAppStripSegments(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
        usages: List<BatteryAppUsageEntity>,
        nowMs: Long,
    ): List<AppStripSegment> {
        if (usages.isEmpty() || samples.isEmpty()) return emptyList()
        val sessionStart = session.startedAt
        val sessionEnd = session.endedAt
            ?: samples.lastOrNull()?.timestamp
            ?: nowMs
        val span = (sessionEnd - sessionStart).coerceAtLeast(1L)
        return usages.map { usage ->
            val endAt = usage.endedAt ?: sessionEnd
            val startRatio = ((usage.startedAt - sessionStart).toFloat() / span).coerceIn(0f, 1f)
            val endRatio = ((endAt - sessionStart).toFloat() / span).coerceIn(startRatio, 1f)
            val app = AppsHelper.apps.value.find { it.packageName == usage.packageName }
            AppStripSegment(
                packageName = usage.packageName,
                startRatio = startRatio,
                endRatio = endRatio,
                iconPath = app?.iconPath?.takeIf { it.isNotBlank() }
                    ?: AppsHelper.getIconPath(usage.packageName).takeIf { it.isNotBlank() },
            )
        }
    }

    fun buildAppUsageRows(
        usages: List<BatteryAppUsageEntity>,
        samplesByUsageId: Map<Long, List<BatteryRecordSampleEntity>>,
        nowMs: Long,
    ): List<DischargeStatisticsViewModel.AppUsageRow> {
        if (usages.isEmpty()) return emptyList()
        data class Acc(
            var durationMs: Long = 0L,
            var powerSumUw: Double = 0.0,
            var powerCount: Int = 0,
            var tempSum: Double = 0.0,
            var tempCount: Int = 0,
            var maxTemp: Float = Float.NEGATIVE_INFINITY,
        )
        val byPackage = linkedMapOf<String, Acc>()
        for (usage in usages) {
            val samples = samplesByUsageId[usage.id].orEmpty()
            val duration = resolveUsageDurationMs(usage, samples, nowMs)
            val acc = byPackage.getOrPut(usage.packageName) { Acc() }
            acc.durationMs += duration
            for (sample in samples) {
                val power = samplePowerUw(sample)
                if (power != null) {
                    acc.powerSumUw += power.toDouble()
                    acc.powerCount += 1
                }
                val temp = sample.temperatureC
                if (temp != null) {
                    acc.tempSum += temp.toDouble()
                    acc.tempCount += 1
                    if (temp > acc.maxTemp) acc.maxTemp = temp
                }
            }
        }
        return byPackage.map { (packageName, acc) ->
            val app = AppsHelper.apps.value.find { it.packageName == packageName }
            val avgPowerUw = if (acc.powerCount > 0) {
                (acc.powerSumUw / acc.powerCount).toLong()
            } else {
                0L
            }
            DischargeStatisticsViewModel.AppUsageRow(
                packageName = packageName,
                appName = app?.appName?.takeIf { it.isNotBlank() } ?: packageName,
                iconPath = app?.iconPath?.takeIf { it.isNotBlank() }
                    ?: AppsHelper.getIconPath(packageName).takeIf { it.isNotBlank() },
                avgW = avgPowerUw / 1_000_000f,
                avgTemp = if (acc.tempCount > 0) (acc.tempSum / acc.tempCount).toFloat() else 0f,
                maxTemp = if (acc.maxTemp.isFinite()) acc.maxTemp else 0f,
                durationMs = acc.durationMs,
            )
        }
    }

    fun buildBatteryInfoRow(
        samples: List<BatteryRecordSampleEntity>,
        energyUw: Long,
    ): DischargeStatisticsViewModel.BatteryInfoRow? {
        val last = samples.lastOrNull() ?: return null
        return DischargeStatisticsViewModel.BatteryInfoRow(
            energyUw = energyUw,
            temperatureC = last.temperatureC,
            voltageMv = last.voltageMv,
        )
    }

    private fun resolveUsageDurationMs(
        usage: BatteryAppUsageEntity,
        samples: List<BatteryRecordSampleEntity>,
        nowMs: Long,
    ): Long {
        val ended = usage.endedAt
        if (ended != null) {
            return (ended - usage.startedAt).coerceAtLeast(0L)
        }
        if (samples.size >= 2) {
            return (samples.last().timestamp - samples.first().timestamp).coerceAtLeast(0L)
        }
        if (samples.isNotEmpty()) {
            return (samples.last().timestamp - usage.startedAt).coerceAtLeast(0L)
        }
        return (nowMs - usage.startedAt).coerceAtLeast(0L)
    }

    private fun averagePowerUw(samples: List<BatteryRecordSampleEntity>): Long {
        var sum = 0L
        var count = 0
        for (sample in samples) {
            val power = samplePowerUw(sample) ?: continue
            sum += power
            count += 1
        }
        return if (count <= 0) 0L else sum / count
    }

    private fun samplePowerUw(sample: BatteryRecordSampleEntity): Long? {
        val currentMa = sample.currentMa ?: return null
        val voltageMv = sample.voltageMv ?: return null
        return abs(currentMa.toLong() * voltageMv.toLong())
    }
}
