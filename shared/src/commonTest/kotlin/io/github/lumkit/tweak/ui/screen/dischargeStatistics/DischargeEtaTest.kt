package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DischargeEtaTest {

    @Test
    fun recentWindowBeatsWholeSessionAverage() {
        // 前 1h 高负载掉 20%，后 20min 待机只掉 1%。
        // 旧算法用整段 21%/80min，剩余 59% 只有约 3.7h；近期窗口应接近待机续航。
        val heavy = buildSamples(
            startMs = 0L,
            durationMs = 60 * 60_000L,
            startLevel = 80,
            endLevel = 60,
            currentMa = -2500,
        )
        val idle = buildSamples(
            startMs = 60 * 60_000L,
            durationMs = 20 * 60_000L,
            startLevel = 60,
            endLevel = 59,
            currentMa = -180,
            idOffset = heavy.size.toLong(),
        )
        val samples = heavy + idle
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                samples = samples,
                capacityMah = 5000,
                remainingLevel = 59,
                isLiveSession = true,
            ),
        )
        val eta = requireNotNull(ms)
        val wholeSessionEta = (80 * 60_000L).toDouble() / 21.0 * 59.0
        assertTrue(
            eta > wholeSessionEta.toLong() + 2 * 60 * 60_000L,
            "ETA should follow recent idle drain, got $eta vs whole-session $wholeSessionEta",
        )
    }

    @Test
    fun socSlopeUsedWhenDropIsEnough() {
        val samples = buildSamples(
            startMs = 0L,
            durationMs = 60 * 60_000L,
            startLevel = 60,
            endLevel = 50,
            currentMa = -800,
        )
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                samples = samples,
                capacityMah = 5000,
                remainingLevel = 50,
                isLiveSession = true,
            ),
        )
        // 1h 掉 10%，剩余 50% → 5h
        val eta = requireNotNull(ms)
        assertTrue(
            abs(eta - 18_000_000L) < 20 * 60_000L,
            "expected ~5h, got $eta",
        )
    }

    @Test
    fun currentFallbackWhenSocHasNotMoved() {
        val samples = buildSamples(
            startMs = 0L,
            durationMs = 12 * 60_000L,
            startLevel = 80,
            endLevel = 80,
            currentMa = -500,
        )
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                samples = samples,
                capacityMah = 4000,
                remainingLevel = 80,
                isLiveSession = true,
            ),
        )
        // 4000*0.8 / 500mA = 6.4h
        val expected = (4000 * 80 / 100.0 / 500.0 * 3_600_000.0).toLong()
        val eta = requireNotNull(ms)
        assertTrue(abs(eta - expected) < 15 * 60_000L, "expected ~$expected got $eta")
    }

    @Test
    fun ignoresInstantSocJump() {
        val stable = buildSamples(
            startMs = 0L,
            durationMs = 10 * 60_000L,
            startLevel = 50,
            endLevel = 50,
            currentMa = -400,
        )
        val jump = listOf(
            sample(
                id = 999,
                timestamp = 10 * 60_000L + 1_000L,
                level = 48,
                currentMa = -400,
            ),
        )
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                samples = stable + jump,
                capacityMah = 4000,
                remainingLevel = 48,
                isLiveSession = true,
            ),
        )
        val eta = requireNotNull(ms)
        // 末尾 2%/1s 跳变不走 SoC，改电流法
        assertTrue(eta > 30 * 60_000L, "SoC jump must not collapse ETA, got $eta")
    }

    @Test
    fun returnsNullWhenRemainingIsZero() {
        assertNull(
            estimateDischargeEtaMs(
                DischargeEtaInput(
                    samples = buildSamples(0L, 10 * 60_000L, 10, 0, -500),
                    capacityMah = 4000,
                    remainingLevel = 0,
                    isLiveSession = true,
                ),
            ),
        )
    }

    @Test
    fun returnsNullWhenNoInputs() {
        assertNull(
            estimateDischargeEtaMs(
                DischargeEtaInput(
                    samples = emptyList(),
                    capacityMah = null,
                    remainingLevel = 50,
                    isLiveSession = true,
                ),
            ),
        )
    }

    @Test
    fun smoothingDoesNotSnapOnSingleUpdate() {
        val samples = buildSamples(0L, 30 * 60_000L, 40, 34, -600)
        val raw = estimateDischargeEtaMs(
            DischargeEtaInput(
                samples = samples,
                capacityMah = 4000,
                remainingLevel = 34,
                isLiveSession = true,
                previousEtaMs = null,
            ),
        )
        val baseline = requireNotNull(raw)
        val smoothed = requireNotNull(
            estimateDischargeEtaMs(
                DischargeEtaInput(
                    samples = samples,
                    capacityMah = 4000,
                    remainingLevel = 34,
                    isLiveSession = true,
                    previousEtaMs = baseline * 3,
                ),
            ),
        )
        assertTrue(smoothed > baseline, "should ease from previous 3x value")
        assertTrue(smoothed < baseline * 3, "should move toward new estimate")
    }

    @Test
    fun timeWeightedCurrentIgnoresChargeSpikes() {
        val window = listOf(
            sample(1, 0L, 50, -400),
            sample(2, 10_000L, 50, 2000),
            sample(3, 20_000L, 50, -400),
            sample(4, 30_000L, 50, -400),
        )
        val drain = requireNotNull(timeWeightedDischargeMa(window))
        assertTrue(abs(drain - 400.0) < 1.0, "charge sample must not inflate drain, got $drain")
    }

    private fun buildSamples(
        startMs: Long,
        durationMs: Long,
        startLevel: Int,
        endLevel: Int,
        currentMa: Int,
        intervalMs: Long = 10_000L,
        idOffset: Long = 0L,
    ): List<BatteryRecordSampleEntity> {
        val count = (durationMs / intervalMs).toInt().coerceAtLeast(1) + 1
        return List(count) { index ->
            val t = startMs + index * intervalMs
            val progress = if (count == 1) 1.0 else index.toDouble() / (count - 1)
            val level = (startLevel + (endLevel - startLevel) * progress).toInt()
            sample(
                id = idOffset + index + 1,
                timestamp = t,
                level = level,
                currentMa = currentMa,
            )
        }
    }

    private fun sample(
        id: Long,
        timestamp: Long,
        level: Int,
        currentMa: Int,
    ) = BatteryRecordSampleEntity(
        id = id,
        sessionId = 1L,
        timestamp = timestamp,
        level = level,
        voltageMv = 3800,
        temperatureC = 32f,
        screenOn = true,
        currentMa = currentMa,
    )
}
