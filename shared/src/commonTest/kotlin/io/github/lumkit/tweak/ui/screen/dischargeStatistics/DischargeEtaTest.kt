package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DischargeEtaTest {

    @Test
    fun prefersCapacityOverSlope() {
        val designMah = 5000
        val avgPowerUw = 3_530_000L
        val expected = (
            designMah / 1000.0 * 3.7 / (avgPowerUw / 1_000_000.0) * 3_600_000.0
            ).toLong()
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                designCapacityMah = designMah,
                avgPowerUw = avgPowerUw,
                durationMs = 43 * 60_000L,
                startLevel = 100,
                endLevel = 80,
                remainingLevel = 80,
            ),
        )
        assertEquals(expected, ms)
    }

    @Test
    fun fallsBackToFullSlopeWhenNoCapacity() {
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                designCapacityMah = null,
                avgPowerUw = 0L,
                durationMs = 3_600_000L,
                startLevel = 100,
                endLevel = 50,
                remainingLevel = 50,
            ),
        )
        assertEquals(7_200_000L, ms)
    }

    @Test
    fun returnsNullWhenNoValidInputs() {
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                designCapacityMah = null,
                avgPowerUw = 0L,
                durationMs = 0L,
                startLevel = 50,
                endLevel = 50,
                remainingLevel = 50,
            ),
        )
        assertNull(ms)
    }

    @Test
    fun capacityPathIsPositive() {
        val ms = estimateDischargeEtaMs(
            DischargeEtaInput(
                designCapacityMah = 4000,
                avgPowerUw = 2_000_000L,
                durationMs = 1_000L,
                startLevel = 90,
                endLevel = 89,
                remainingLevel = 89,
            ),
        )
        assertNotNull(ms)
        assertTrue(ms!! > 0L)
    }
}
