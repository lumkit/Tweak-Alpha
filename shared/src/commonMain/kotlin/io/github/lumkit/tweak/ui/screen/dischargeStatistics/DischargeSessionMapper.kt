package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import androidx.compose.ui.graphics.Color
import io.github.lumkit.tweak.common.component.AppMinuteBarCell
import io.github.lumkit.tweak.common.component.AppMinuteBarColumn
import io.github.lumkit.tweak.common.component.LineChartData
import io.github.lumkit.tweak.common.component.LineChartXAxisData
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryUidPowerEntity
import io.github.lumkit.tweak.common.utils.AppsHelper
import io.github.lumkit.tweak.common.utils.formatElapsedTime
import io.github.lumkit.tweak.ui.screen.chargeStatistics.ChargeSessionChartsMapper
import kotlin.math.abs

internal object DischargeSessionMapper {

    private const val FIVE_MIN_MS = 5 * 60_000L
    private const val MIN_DRAW_SPAN_MS = 30 * 60_000L
    private const val DRAW_INTERVAL_COUNT = 6
    private const val MAX_X_TICK_COUNT = 6
    private const val ICON_SLOTS_PER_INTERVAL = 5
    private const val LEVEL_POINTS_PER_INTERVAL = 20

    /**
     * 放电过程图时间轴：
     * - 区间宽度为 5 分钟的整数倍
     * - 录制 ≤30 分钟时按 30 分钟绘制（区间=5 分钟）
     * - 超出后放大区间，始终 6 个绘制区间，X 轴最多 6 个刻度（0..5I，末端多留 1 个区间）
     * - 每个 X 轴区间再均分 5 格绘制 App 图标
     */
    data class ChartAxisScale(
        val intervalMs: Long,
        val drawIntervalCount: Int = DRAW_INTERVAL_COUNT,
        val labeledPointCount: Int = MAX_X_TICK_COUNT,
        val iconSlotsPerInterval: Int = ICON_SLOTS_PER_INTERVAL,
        val levelPointsPerInterval: Int = LEVEL_POINTS_PER_INTERVAL,
    ) {
        val drawSpanMs: Long get() = intervalMs * drawIntervalCount
        val iconSlotCount: Int get() = drawIntervalCount * iconSlotsPerInterval
        val levelPointCount: Int get() = drawIntervalCount * levelPointsPerInterval + 1

        fun xTickIndexes(): List<Int> {
            return (0 until labeledPointCount).map { tick ->
                tick * levelPointsPerInterval
            }
        }
    }

    data class LevelChartBuild(
        val xAxis: LineChartXAxisData,
        val series: LineChartData,
        val xTickIndexes: List<Int>,
        val axisScale: ChartAxisScale,
    )

    fun resolveAxisScale(durationMs: Long): ChartAxisScale {
        val duration = durationMs.coerceAtLeast(0L)
        val intervalMs = if (duration <= MIN_DRAW_SPAN_MS) {
            FIVE_MIN_MS
        } else {
            val rawPerInterval = (duration + DRAW_INTERVAL_COUNT - 1) / DRAW_INTERVAL_COUNT
            ((rawPerInterval + FIVE_MIN_MS - 1) / FIVE_MIN_MS) * FIVE_MIN_MS
        }
        return ChartAxisScale(intervalMs = intervalMs)
    }

    fun buildSummary(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
        nowMs: Long,
        liveActiveSessionId: Long? = null,
    ): DischargeStatisticsViewModel.DischargeSessionSummary? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        // 仅「当前仍在采样的活跃会话」实时计时；endedAt 为空的历史脏数据冻结在末采样点
        val isOngoing =
            session.endedAt == null &&
                session.id == liveActiveSessionId &&
                session.chargeState == BatteryChargeState.DISCHARGING
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
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
        seriesName: String,
        color: Color,
        nowMs: Long,
    ): LevelChartBuild? {
        if (samples.isEmpty()) return null
        val sessionStart = session.startedAt
        val sessionEnd = session.endedAt
            ?: samples.lastOrNull()?.timestamp
            ?: nowMs
        val durationMs = (sessionEnd - sessionStart).coerceAtLeast(0L)
        val scale = resolveAxisScale(durationMs)
        val pointCount = scale.levelPointCount
        val drawSpan = scale.drawSpanMs

        val labels = ArrayList<String>(pointCount)
        val levels = ArrayList<Float>(pointCount)
        for (index in 0 until pointCount) {
            val elapsedMs = drawSpan * index / (pointCount - 1)
            labels += elapsedMs.formatElapsedTime()
            levels += levelAtElapsed(samples, sessionStart, elapsedMs)
        }

        return LevelChartBuild(
            xAxis = LineChartXAxisData(labels),
            series = LineChartData(
                name = seriesName,
                suffix = "%",
                dataSet = levels,
                color = color,
            ),
            xTickIndexes = scale.xTickIndexes(),
            axisScale = scale,
        )
    }

    /**
     * 在绘制时间轴上按「区间/5」分槽聚合前台应用功耗；
     * 每列 [cells] 按功耗升序（低在下、高在上）。
     */
    fun buildAppMinuteColumns(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
        usages: List<BatteryAppUsageEntity>,
        nowMs: Long,
        axisScale: ChartAxisScale,
    ): List<AppMinuteBarColumn> {
        if (usages.isEmpty() || samples.isEmpty()) return emptyList()
        val sessionStart = session.startedAt
        val sessionEnd = session.endedAt
            ?: samples.lastOrNull()?.timestamp
            ?: nowMs
        val sortedUsages = usages.sortedBy { it.startedAt }
        val slotCount = axisScale.iconSlotCount
        val drawSpan = axisScale.drawSpanMs
        val columns = ArrayList<AppMinuteBarColumn>(slotCount)

        for (slotIndex in 0 until slotCount) {
            val bucketStart = sessionStart + drawSpan * slotIndex / slotCount
            val bucketEnd = if (slotIndex == slotCount - 1) {
                sessionStart + drawSpan
            } else {
                sessionStart + drawSpan * (slotIndex + 1) / slotCount
            }
            if (bucketEnd <= bucketStart) continue

            val powerSums = LinkedHashMap<String, Long>()
            val powerCounts = LinkedHashMap<String, Int>()
            for (sample in samples) {
                if (sample.timestamp < bucketStart || sample.timestamp >= bucketEnd) continue
                if (sample.timestamp > sessionEnd) continue
                val pkg = packageAt(sample.timestamp, sortedUsages) ?: continue
                if (pkg.isBlank()) continue
                val power = samplePowerUw(sample) ?: continue
                powerSums[pkg] = (powerSums[pkg] ?: 0L) + power
                powerCounts[pkg] = (powerCounts[pkg] ?: 0) + 1
            }
            if (powerSums.isEmpty()) continue

            val cells = powerSums.map { (pkg, sum) ->
                val count = (powerCounts[pkg] ?: 1).coerceAtLeast(1)
                val app = AppsHelper.apps.value.find { it.packageName == pkg }
                AppMinuteBarCell(
                    packageName = pkg,
                    iconPath = app?.iconPath?.takeIf { it.isNotBlank() }
                        ?: AppsHelper.getIconPath(pkg).takeIf { it.isNotBlank() },
                    powerUw = sum / count,
                )
            }.sortedBy { it.powerUw }

            columns.add(
                AppMinuteBarColumn(
                    minuteIndex = slotIndex,
                    centerRatio = ((slotIndex + 0.5f) / slotCount).coerceIn(0f, 1f),
                    cells = cells,
                ),
            )
        }
        return columns
    }

    fun buildUidPowerRows(
        uidPowers: List<BatteryUidPowerEntity>,
        usages: List<BatteryAppUsageEntity>,
        samplesByUsageId: Map<Long, List<BatteryRecordSampleEntity>>,
        avgVoltageMv: Int?,
        sessionDurationMs: Long,
        nowMs: Long,
    ): List<DischargeStatisticsViewModel.AppUsageRow> {
        if (uidPowers.isEmpty()) return emptyList()
        data class UsageAcc(
            var durationMs: Long = 0L,
            var tempSum: Double = 0.0,
            var tempCount: Int = 0,
            var maxTemp: Float = Float.NEGATIVE_INFINITY,
        )
        val usageByPackage = linkedMapOf<String, UsageAcc>()
        for (usage in usages) {
            val samples = samplesByUsageId[usage.id].orEmpty()
            val duration = resolveUsageDurationMs(usage, samples, nowMs)
            val acc = usageByPackage.getOrPut(usage.packageName) { UsageAcc() }
            acc.durationMs += duration
            for (sample in samples) {
                val temp = sample.temperatureC
                if (temp != null) {
                    acc.tempSum += temp.toDouble()
                    acc.tempCount += 1
                    if (temp > acc.maxTemp) acc.maxTemp = temp
                }
            }
        }
        val vNom = avgVoltageMv?.takeIf { it > 0 }?.div(1000.0) ?: 3.7
        val durationHours = sessionDurationMs / 3_600_000.0
        return uidPowers
            .filter { it.deltaMah > 0.0 }
            .map { row ->
                val packageName = row.packageName?.takeIf { it.isNotBlank() } ?: "uid:${row.uid}"
                val usageAcc = row.packageName?.let { usageByPackage[it] }
                val avgW = if (durationHours > 0.0) {
                    ((row.deltaMah / 1000.0) * vNom / durationHours).toFloat()
                } else {
                    0f
                }
                val app = AppsHelper.apps.value.find { it.packageName == packageName }
                DischargeStatisticsViewModel.AppUsageRow(
                    packageName = packageName,
                    appName = app?.appName?.takeIf { it.isNotBlank() }
                        ?: if (row.packageName.isNullOrBlank()) "UID ${row.uid}" else packageName,
                    iconPath = app?.iconPath?.takeIf { it.isNotBlank() }
                        ?: AppsHelper.getIconPath(packageName).takeIf { it.isNotBlank() },
                    avgW = avgW,
                    avgTemp = if (usageAcc != null && usageAcc.tempCount > 0) {
                        (usageAcc.tempSum / usageAcc.tempCount).toFloat()
                    } else {
                        0f
                    },
                    maxTemp = usageAcc?.maxTemp?.takeIf { it.isFinite() } ?: 0f,
                    durationMs = usageAcc?.durationMs ?: 0L,
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

    private fun levelAtElapsed(
        samples: List<BatteryRecordSampleEntity>,
        sessionStart: Long,
        elapsedMs: Long,
    ): Float {
        val target = sessionStart + elapsedMs
        var previous: BatteryRecordSampleEntity? = null
        for (sample in samples) {
            if (sample.timestamp == target) return sample.level.toFloat()
            if (sample.timestamp > target) {
                val prev = previous ?: return sample.level.toFloat()
                val span = (sample.timestamp - prev.timestamp).coerceAtLeast(1L)
                val ratio = (target - prev.timestamp).toFloat() / span
                return prev.level + (sample.level - prev.level) * ratio
            }
            previous = sample
        }
        return previous?.level?.toFloat() ?: samples.first().level.toFloat()
    }

    private fun packageAt(timestamp: Long, sortedUsages: List<BatteryAppUsageEntity>): String? {
        var matched: BatteryAppUsageEntity? = null
        for (usage in sortedUsages) {
            if (timestamp < usage.startedAt) break
            val ended = usage.endedAt
            if (ended == null || timestamp < ended) {
                matched = usage
            }
        }
        return matched?.packageName
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
