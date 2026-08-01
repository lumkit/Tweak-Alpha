package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import io.github.lumkit.tweak.ui.screen.chargeStatistics.formatChargeEnergyGainWh
import io.github.lumkit.tweak.ui.screen.chargeStatistics.formatChargeSessionDuration
import io.github.lumkit.tweak.ui.screen.chargeStatistics.formatChargeSessionStartTitle

fun formatDischargeSessionStartTitle(startedAt: Long): String =
    formatChargeSessionStartTitle(startedAt)

fun formatDischargeSessionDuration(startedAt: Long, endedAt: Long): String =
    formatChargeSessionDuration(startedAt, endedAt)

fun formatDischargeEnergyWh(energyUw: Long): String =
    formatChargeEnergyGainWh(energyUw)

fun formatDischargeEtaText(etaMs: Long?): String {
    if (etaMs == null || etaMs <= 0L) return "--"
    return formatChargeSessionDuration(0L, etaMs)
}

fun formatDischargeLevelDrop(startLevel: Int, endLevel: Int): String {
    val delta = startLevel - endLevel
    return if (delta >= 0) "-${delta}%" else "+${-delta}%"
}

fun formatDischargeSessionSummaryLine(
    summary: DischargeStatisticsViewModel.DischargeSessionSummary,
    nowMs: Long,
): String {
    val endedAt = if (summary.isCurrentDischargingSession) nowMs else summary.endedAt
    return buildString {
        append(formatDischargeSessionDuration(summary.startedAt, endedAt))
        append(" · ")
        append(formatDischargeLevelDrop(summary.startLevel, summary.endLevel))
        append(" · ")
        append(formatDischargeEnergyWh(summary.energyUw))
    }
}

fun formatDischargeAppAvgLine(avgW: Float, avgTemp: Float): String =
    "AVG %.2fW / %.1f℃".format(avgW, avgTemp)

fun formatDischargeAppMaxTemp(maxTemp: Float): String =
    "MAX %.1f℃".format(maxTemp)
