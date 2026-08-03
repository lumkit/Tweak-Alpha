package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DischargeSessionSummaryTest {

    @Test
    fun abandonedOpenSessionFreezesAtLastSample() {
        val session = BatteryRecordSessionEntity(
            id = 1L,
            startedAt = 1_000L,
            intervalMs = 1_000,
            state = BatteryChargeState.DISCHARGING.code,
            endedAt = null,
        )
        val samples = listOf(
            sample(1L, 1_000L, 80),
            sample(2L, 10_000L, 70),
        )
        val summary = DischargeSessionMapper.buildSummary(
            session = session,
            samples = samples,
            nowMs = 999_000L,
            liveActiveSessionId = 99L, // 当前活跃是别的会话
        )
        assertNotNull(summary)
        assertFalse(summary.isCurrentDischargingSession)
        assertEquals(10_000L, summary.endedAt)
    }

    @Test
    fun liveActiveSessionUsesNow() {
        val session = BatteryRecordSessionEntity(
            id = 7L,
            startedAt = 1_000L,
            intervalMs = 1_000,
            state = BatteryChargeState.DISCHARGING.code,
            endedAt = null,
        )
        val samples = listOf(sample(1L, 1_000L, 80), sample(2L, 10_000L, 70))
        val summary = DischargeSessionMapper.buildSummary(
            session = session,
            samples = samples,
            nowMs = 60_000L,
            liveActiveSessionId = 7L,
        )
        assertNotNull(summary)
        assertTrue(summary.isCurrentDischargingSession)
        assertEquals(60_000L, summary.endedAt)
    }

    private fun sample(id: Long, ts: Long, level: Int) = BatteryRecordSampleEntity(
        id = id,
        sessionId = 1L,
        timestamp = ts,
        level = level,
        screenOn = true,
    )
}
