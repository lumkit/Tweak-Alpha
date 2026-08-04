package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlin.math.abs

object BatteryFullCapacityEstimator {

    fun estimateMah(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
    ): Int? = estimateMah(listOf(session to samples))

    fun estimateMah(
        sessions: List<Pair<BatteryRecordSessionEntity, List<BatteryRecordSampleEntity>>>,
    ): Int? {
        var totalChargedMah = 0.0
        var totalLevelGain = 0

        sessions.forEach { (session, samples) ->
            val segment = calculateSegment(session, samples) ?: return@forEach
            totalChargedMah += segment.chargedMah
            totalLevelGain += segment.levelGain
        }

        if (totalChargedMah <= 0.0 || totalLevelGain <= 0) return null
        val estimatedFullMah = totalChargedMah / (totalLevelGain / 100.0)
        return estimatedFullMah.toInt().takeIf { it > 0 }
    }

    private fun calculateSegment(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
    ): CapacitySegment? {
        if (session.endedAt == null || session.deleted || !session.confirmed) return null
        if (samples.size < 2) return null

        val start = samples.first()
        val end = samples.last()
        val levelGain = (end.level - start.level).coerceAtLeast(0)
        if (levelGain <= 0) return null

        var chargedMah = 0.0
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            val currentMa = previous.currentMa ?: continue
            val durationHours =
                (current.timestamp - previous.timestamp).coerceAtLeast(0L) / 3_600_000.0
            chargedMah += abs(currentMa.toDouble()) * durationHours
        }
        if (chargedMah <= 0.0) return null

        return CapacitySegment(
            chargedMah = chargedMah,
            levelGain = levelGain,
        )
    }

    private data class CapacitySegment(
        val chargedMah: Double,
        val levelGain: Int,
    )
}
