package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DischargeEtaTest {

    @Test
    fun slopePreferredWhenDropAndDurationEnough() {
        // 1h 掉 10% → 每 % 需 6min；剩余 50% → 5h
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                capacityMah = 5000,
                avgPowerUw = 1_000_000L,
                avgVoltageMv = 3700,
                durationMs = 3_600_000L,
                startLevel = 60,
                endLevel = 50,
                remainingLevel = 50,
            ),
        )
        assertEquals(18_000_000L, ms)
    }

    @Test
    fun energyFallbackWhenDropBelowThreshold() {
        // 掉 1% < 2 → E: remainingWh/avgW
        val capacity = 5000
        val remaining = 50
        val avgW = 2.0
        val v = 3.7
        val remainingWh = capacity / 1000.0 * v * remaining / 100.0
        val expected = (remainingWh / avgW * 3_600_000.0).toLong()
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                capacityMah = capacity,
                avgPowerUw = 2_000_000L,
                avgVoltageMv = 3700,
                durationMs = 3_600_000L,
                startLevel = 51,
                endLevel = 50,
                remainingLevel = remaining,
            ),
        )
        assertEquals(expected, ms)
    }

    @Test
    fun energyFallbackWhenDurationTooShort() {
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                capacityMah = 4000,
                avgPowerUw = 2_000_000L,
                avgVoltageMv = 3700,
                durationMs = 60_000L, // < 3min
                startLevel = 60,
                endLevel = 50,
                remainingLevel = 50,
            ),
        )
        assertTrue(ms != null && ms > 0L)
        // 不得等于斜率 1min/10%*50 = 5min
        assertTrue(ms != 300_000L)
    }

    @Test
    fun returnsNullWhenNoInputs() {
        assertNull(
            estimateDischargeEtaMs(
                DischargeEtaInput(
                    capacityMah = null,
                    avgPowerUw = 0L,
                    avgVoltageMv = null,
                    durationMs = 0L,
                    startLevel = 50,
                    endLevel = 50,
                    remainingLevel = 50,
                ),
            ),
        )
    }

    @Test
    fun energyUsesProvidedVoltageNotHardcoded() {
        val a = estimateDischargeEtaMs(
            DischargeEtaInput(4000, 2_000_000L, 4000, 0L, 50, 50, 50),
        )
        val b = estimateDischargeEtaMs(
            DischargeEtaInput(4000, 2_000_000L, 3400, 0L, 50, 50, 50),
        )
        assertTrue(a != null && b != null && a != b)
    }

    @Test
    fun returnsNullWhenRemainingIsZero() {
        assertNull(
            estimateDischargeEtaMs(
                DischargeEtaInput(
                    capacityMah = 4000,
                    avgPowerUw = 2_000_000L,
                    avgVoltageMv = 3700,
                    durationMs = 3_600_000L,
                    startLevel = 10,
                    endLevel = 0,
                    remainingLevel = 0,
                ),
            ),
        )
    }
}
