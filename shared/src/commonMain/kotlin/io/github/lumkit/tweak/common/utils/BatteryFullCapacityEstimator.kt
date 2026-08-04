package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlin.math.abs

object BatteryFullCapacityEstimator {

    fun estimateMah(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
    ): Int? {
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

        val estimatedFullMah = chargedMah / (levelGain / 100.0)
        return estimatedFullMah.toInt().takeIf { it > 0 }
    }
}
