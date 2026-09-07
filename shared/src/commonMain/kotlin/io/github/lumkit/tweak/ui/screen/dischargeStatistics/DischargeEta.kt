package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 剩余续航估算输入（到 0%）。
 *
 * @param samples 本段放电会话采样，按时间先后均可（内部会排序）
 * @param capacityMah 满充/估算/设计容量；SoC 尚未明显下降时用于电流法
 * @param remainingLevel 当前剩余电量 %
 * @param isLiveSession 是否为正在放电的会话（用更短的近期窗口）
 * @param previousEtaMs 上一轮结果，用于平滑，避免采样噪声来回跳
 */
data class DischargeEtaInput(
    val samples: List<BatteryRecordSampleEntity>,
    val capacityMah: Int?,
    val remainingLevel: Int,
    val isLiveSession: Boolean,
    val previousEtaMs: Long? = null,
)

private const val LIVE_WINDOW_MS = 20 * 60_000L
private const val HISTORY_WINDOW_MS = 45 * 60_000L
private const val MAX_WINDOW_MS = 2 * 60 * 60_000L
private const val MIN_SOC_DURATION_MS = 2 * 60_000L
private const val MIN_SOC_DROP = 1
private const val MIN_MS_PER_PERCENT = 20_000L
private const val MAX_MS_PER_PERCENT = 8 * 60 * 60_000L
private const val MAX_INTERVAL_MS = 5 * 60_000L
private const val MAX_ABS_CURRENT_MA = 12_000
private const val MIN_ETA_MS = 60_000L
private const val MAX_ETA_MS = 7 * 24 * 60 * 60_000L
private const val SMOOTH_ALPHA = 0.28
private const val SMOOTH_ALPHA_LARGE = 0.62

/**
 * 剩余续航（毫秒）。
 *
 * 用近期窗口而不是整段会话：整段首尾斜率会被前半段高负载/后半段待机稀释，
 * 电量 1% 量化跳变也会把 ETA 打飞。
 *
 * - SoC：窗口内净掉电 / 时长（校准无关，掉电足够时优先）
 * - 电流：时间加权平均放电电流 × 容量（SoC 还没动时的兜底）
 * - 两者都可用时按置信度加权，再对上一轮结果做 EMA
 */
fun estimateDischargeEtaMs(input: DischargeEtaInput): Long? {
    val remaining = input.remainingLevel.coerceIn(0, 100)
    if (remaining <= 0) return null

    val sorted = input.samples
        .asSequence()
        .filter { it.timestamp > 0L }
        .sortedBy { it.timestamp }
        .toList()
    if (sorted.size < 2) return null

    val window = selectEstimateWindow(sorted, input.isLiveSession)
    if (window.size < 2) return null

    val socEta = estimateBySocSlope(window, remaining)
    val currentEta = estimateByCurrent(window, input.capacityMah, remaining)

    val blended = blendEstimates(socEta, currentEta) ?: return null
    val clamped = blended.coerceIn(MIN_ETA_MS, MAX_ETA_MS)
    return smoothEta(input.previousEtaMs, clamped)
}

internal data class WeightedEta(
    val etaMs: Long,
    val confidence: Double,
)

internal fun selectEstimateWindow(
    sorted: List<BatteryRecordSampleEntity>,
    isLiveSession: Boolean,
): List<BatteryRecordSampleEntity> {
    val endT = sorted.last().timestamp
    val preferred = if (isLiveSession) LIVE_WINDOW_MS else HISTORY_WINDOW_MS
    var windowMs = preferred
    var window = sliceFrom(sorted, endT - windowMs)

    while (window.size >= 2 &&
        netDrop(window) < MIN_SOC_DROP &&
        windowMs < MAX_WINDOW_MS
    ) {
        windowMs = min(windowMs + preferred, MAX_WINDOW_MS)
        window = sliceFrom(sorted, endT - windowMs)
    }
    if (window.size < 2) {
        window = sorted
    }
    return snapToLevelPlateauStart(sorted, window)
}

private fun sliceFrom(
    sorted: List<BatteryRecordSampleEntity>,
    startT: Long,
): List<BatteryRecordSampleEntity> {
    val index = sorted.indexOfFirst { it.timestamp >= startT }
    if (index < 0) return emptyList()
    return sorted.subList(index, sorted.size)
}

/**
 * 窗口若从某电量平台中段切开，会少算这一格的时长。
 * 向前对齐到该电量第一次出现的采样。
 */
private fun snapToLevelPlateauStart(
    sorted: List<BatteryRecordSampleEntity>,
    window: List<BatteryRecordSampleEntity>,
): List<BatteryRecordSampleEntity> {
    if (window.isEmpty()) return window
    val first = window.first()
    var index = sorted.indexOfFirst { it.id == first.id && it.timestamp == first.timestamp }
    if (index < 0) {
        index = sorted.indexOfFirst { it.timestamp == first.timestamp }
    }
    if (index <= 0) return window
    val level = first.level
    var start = index
    while (start > 0 && sorted[start - 1].level == level) {
        start--
    }
    return sorted.subList(start, sorted.size)
}

internal fun estimateBySocSlope(
    window: List<BatteryRecordSampleEntity>,
    remaining: Int,
): WeightedEta? {
    val durationMs = window.last().timestamp - window.first().timestamp
    if (durationMs < MIN_SOC_DURATION_MS) return null

    val dropped = netDrop(window)
    if (dropped < MIN_SOC_DROP) return null

    val lastDrop = window[window.size - 2].level - window.last().level
    val lastDt = window.last().timestamp - window[window.size - 2].timestamp
    if (lastDrop >= 2 && lastDt < 30_000L && lastDrop >= dropped) {
        return null
    }

    val msPerPercent = durationMs.toDouble() / dropped
    if (msPerPercent < MIN_MS_PER_PERCENT || msPerPercent > MAX_MS_PER_PERCENT) {
        return null
    }

    val etaMs = (msPerPercent * remaining).toLong().takeIf { it > 0L } ?: return null
    val confidence = when {
        dropped >= 5 && durationMs >= 10 * 60_000L -> 0.90
        dropped >= 3 && durationMs >= 8 * 60_000L -> 0.78
        dropped >= 2 && durationMs >= 5 * 60_000L -> 0.62
        dropped >= 1 && durationMs >= 8 * 60_000L -> 0.45
        else -> 0.28
    }
    return WeightedEta(etaMs, confidence)
}

internal fun estimateByCurrent(
    window: List<BatteryRecordSampleEntity>,
    capacityMah: Int?,
    remaining: Int,
): WeightedEta? {
    val cap = capacityMah?.takeIf { it > 0 } ?: return null
    val drainMa = timeWeightedDischargeMa(window) ?: return null
    if (drainMa < 20.0) return null

    val remainingMah = cap * remaining / 100.0
    val etaMs = (remainingMah / drainMa * 3_600_000.0).toLong().takeIf { it > 0L } ?: return null

    val coverage = currentCoverage(window)
    val confidence = when {
        coverage >= 0.8 && drainMa >= 80.0 -> 0.55
        coverage >= 0.5 -> 0.40
        else -> 0.22
    }
    return WeightedEta(etaMs, confidence)
}

internal fun timeWeightedDischargeMa(window: List<BatteryRecordSampleEntity>): Double? {
    if (window.size < 2) return null
    var mah = 0.0
    var hours = 0.0
    for (index in 1 until window.size) {
        val previous = window[index - 1]
        val current = window[index]
        val dtMs = current.timestamp - previous.timestamp
        if (dtMs <= 0L || dtMs > MAX_INTERVAL_MS) continue
        val currentMa = previous.currentMa ?: continue
        if (currentMa > 0) continue
        val absMa = abs(currentMa)
        if (absMa <= 0 || absMa > MAX_ABS_CURRENT_MA) continue
        val dtHours = dtMs / 3_600_000.0
        mah += absMa * dtHours
        hours += dtHours
    }
    if (hours <= 0.0 || mah <= 0.0) return null
    return mah / hours
}

private fun currentCoverage(window: List<BatteryRecordSampleEntity>): Double {
    if (window.size < 2) return 0.0
    var valid = 0.0
    var total = 0.0
    for (index in 1 until window.size) {
        val dtMs = (window[index].timestamp - window[index - 1].timestamp).coerceAtLeast(0L)
        if (dtMs <= 0L || dtMs > MAX_INTERVAL_MS) continue
        total += dtMs
        val currentMa = window[index - 1].currentMa
        if (currentMa != null && currentMa <= 0 && abs(currentMa) in 1..MAX_ABS_CURRENT_MA) {
            valid += dtMs
        }
    }
    if (total <= 0.0) return 0.0
    return valid / total
}

private fun netDrop(window: List<BatteryRecordSampleEntity>): Int {
    return (window.first().level - window.last().level).coerceAtLeast(0)
}

private fun blendEstimates(soc: WeightedEta?, current: WeightedEta?): Long? {
    if (soc == null) return current?.etaMs
    if (current == null) return soc.etaMs

    val lo = min(soc.etaMs, current.etaMs).toDouble()
    val hi = max(soc.etaMs, current.etaMs).toDouble()
    if (lo > 0.0 && hi / lo >= 2.4) {
        return if (soc.confidence >= current.confidence) soc.etaMs else current.etaMs
    }

    val weight = soc.confidence + current.confidence
    if (weight <= 0.0) return soc.etaMs
    return ((soc.etaMs * soc.confidence + current.etaMs * current.confidence) / weight).toLong()
}

private fun smoothEta(previousMs: Long?, nextMs: Long): Long {
    if (previousMs == null || previousMs <= 0L) return nextMs
    val lo = min(previousMs, nextMs).toDouble()
    val hi = max(previousMs, nextMs).toDouble()
    val ratio = if (lo > 0.0) hi / lo else 1.0
    val alpha = when {
        ratio >= 3.0 -> SMOOTH_ALPHA_LARGE
        ratio >= 1.8 -> 0.42
        else -> SMOOTH_ALPHA
    }
    return (previousMs * (1.0 - alpha) + nextMs * alpha).toLong()
        .coerceIn(MIN_ETA_MS, MAX_ETA_MS)
}
