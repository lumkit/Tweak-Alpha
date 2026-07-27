package io.github.lumkit.tweak.ui.screen.chargeStatistics

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun formatChargeSessionStartTitle(startedAt: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(startedAt).toLocalDateTime(zone)
    return "%04d-%02d-%02d %02d:%02d:%02d".format(
        start.year,
        start.month.number,
        start.day,
        start.hour,
        start.minute,
        start.second,
    )
}

fun buildChargeSessionTimeText(startedAt: Long, endedAt: Long, showNow: Boolean): String {
    val zone = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(startedAt).toLocalDateTime(zone)
    val end = Instant.fromEpochMilliseconds(endedAt).toLocalDateTime(zone)
    val startText = formatChargeSessionStartTitle(startedAt)
    val endText = if (showNow) {
        "现在"
    } else if (
        start.year == end.year &&
        start.month.number == end.month.number &&
        start.day == end.day
    ) {
        "%02d:%02d:%02d".format(end.hour, end.minute, end.second)
    } else {
        "%04d-%02d-%02d %02d:%02d:%02d".format(
            end.year,
            end.month.number,
            end.day,
            end.hour,
            end.minute,
            end.second,
        )
    }
    return "$startText ~ $endText"
}

fun formatChargeSessionDuration(startedAt: Long, endedAt: Long): String {
    val elapsed = (endedAt - startedAt).coerceAtLeast(0L)
    val second = 1_000L
    val minute = 60 * second
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        elapsed >= day -> buildString {
            val days = elapsed / day
            val hours = (elapsed % day) / hour
            append("${days}天")
            if (hours > 0) append("${hours}小时")
        }
        elapsed >= hour -> buildString {
            val hours = elapsed / hour
            val minutes = (elapsed % hour) / minute
            append("${hours}小时")
            if (minutes > 0) append("${minutes}分钟")
        }
        elapsed >= minute -> buildString {
            val minutes = elapsed / minute
            val seconds = (elapsed % minute) / second
            append("${minutes}分钟")
            if (seconds > 0) append("${seconds}秒")
        }
        else -> "${elapsed / second}秒"
    }
}

fun formatChargeLevelGain(startLevel: Int, endLevel: Int): String {
    val delta = endLevel - startLevel
    return if (delta >= 0) "+${delta}%" else "${delta}%"
}

fun formatChargeEnergyGainWh(energyGainUw: Long): String {
    val wh = energyGainUw / 1_000_000L
    val fraction = kotlin.math.abs((energyGainUw % 1_000_000L) / 10_000L)
    return if (fraction == 0L) {
        "${wh}Wh"
    } else {
        "${wh}.${fraction.toString().padStart(2, '0')}Wh"
    }
}

/** 与充电卡片底部摘要同一套文案，用于历史列表副标题 */
fun formatChargeSessionSummaryLine(
    summary: ChargeStatisticsViewModel.ChargingSessionSummary,
    nowMs: Long,
): String {
    val endedAt = if (summary.isCurrentChargingSession) nowMs else summary.endedAt
    val endLevel = summary.endLevel ?: summary.startLevel
    return buildString {
        append(formatChargeSessionDuration(summary.startedAt, endedAt))
        append(" · ")
        append(formatChargeLevelGain(summary.startLevel, endLevel))
        append(" · ")
        append(formatChargeEnergyGainWh(summary.energyGainUw))
    }
}
