package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DischargeSessionSummaryTest {

    @Test
    fun openUsageDurationClampedToFrozenSessionEnd() {
        // 历史/废弃会话已冻结在 lastSample=10_000，但 usage 未闭合；
        // 旧逻辑用墙钟 now 会算出远超会话的时长。
        val usage = BatteryAppUsageEntity(
            id = 1L,
            sessionId = 1L,
            packageName = "com.example.app",
            startedAt = 1_000L,
            endedAt = null,
        )
        val duration = DischargeSessionMapper.resolveUsageDurationMs(
            usage = usage,
            sessionStartMs = 1_000L,
            sessionEndMs = 10_000L,
        )
        assertEquals(9_000L, duration)
    }

    @Test
    fun closedUsagePastSessionEndIsClamped() {
        val usage = BatteryAppUsageEntity(
            id = 2L,
            sessionId = 1L,
            packageName = "com.example.app",
            startedAt = 1_000L,
            endedAt = 50_000L,
        )
        val duration = DischargeSessionMapper.resolveUsageDurationMs(
            usage = usage,
            sessionStartMs = 1_000L,
            sessionEndMs = 10_000L,
        )
        assertEquals(9_000L, duration)
    }

    @Test
    fun appUsageRowsNeverExceedSessionDuration() {
        val sessionStart = 1_000L
        val sessionDuration = 9_000L
        val usages = listOf(
            BatteryAppUsageEntity(
                id = 1L,
                sessionId = 1L,
                packageName = "com.example.app",
                startedAt = 1_000L,
                endedAt = null,
            ),
            BatteryAppUsageEntity(
                id = 2L,
                sessionId = 1L,
                packageName = "com.example.app",
                startedAt = 2_000L,
                endedAt = null,
            ),
        )
        val rows = DischargeSessionMapper.buildAppUsageRows(
            usages = usages,
            samplesByUsageId = emptyMap(),
            sessionStartMs = sessionStart,
            sessionDurationMs = sessionDuration,
        )
        assertEquals(1, rows.size)
        assertTrue(rows.single().durationMs <= sessionDuration)
        assertEquals(sessionDuration, rows.single().durationMs)
    }

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
