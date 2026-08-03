package io.github.lumkit.tweak.ui.screen.chargeStatistics

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChargeSessionSummaryTest {

    @Test
    fun abandonedOpenSessionIsNotCurrent() {
        val session = BatteryRecordSessionEntity(
            id = 1L,
            startedAt = 1_000L,
            intervalMs = 1_000,
            state = BatteryChargeState.CHARGING.code,
            endedAt = null,
            confirmed = true,
        )
        val samples = listOf(
            sample(1L, 1_000L, 20),
            sample(2L, 10_000L, 50),
        )
        val summary = ChargeSessionChartsMapper.buildSummary(
            session = session,
            samples = samples,
            liveActiveSessionId = 99L,
        )
        assertNotNull(summary)
        assertFalse(summary.isCurrentChargingSession)
        assertEquals(10_000L, summary.endedAt)
    }

    @Test
    fun liveActiveChargingSessionIsCurrent() {
        val session = BatteryRecordSessionEntity(
            id = 7L,
            startedAt = 1_000L,
            intervalMs = 1_000,
            state = BatteryChargeState.CHARGING.code,
            endedAt = null,
            confirmed = true,
        )
        val samples = listOf(sample(1L, 1_000L, 20), sample(2L, 10_000L, 50))
        val summary = ChargeSessionChartsMapper.buildSummary(
            session = session,
            samples = samples,
            liveActiveSessionId = 7L,
        )
        assertNotNull(summary)
        assertTrue(summary.isCurrentChargingSession)
        assertEquals(10_000L, summary.endedAt)
    }

    private fun sample(id: Long, ts: Long, level: Int) = BatteryRecordSampleEntity(
        id = id,
        sessionId = 1L,
        timestamp = ts,
        level = level,
        screenOn = true,
    )
}
