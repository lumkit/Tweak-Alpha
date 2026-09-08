package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryUidPowerEntity
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
            samples = emptyList(),
            sessionStartMs = sessionStart,
            sessionDurationMs = sessionDuration,
        )
        assertEquals(1, rows.size)
        assertTrue(rows.single().durationMs <= sessionDuration)
        assertEquals(sessionDuration, rows.single().durationMs)
    }

    @Test
    fun sceneRowsUseForegroundUsagesWithTempAndDuration() {
        val sessionStart = 1_000L
        val sessionDuration = 10_000L
        val usages = listOf(
            BatteryAppUsageEntity(
                id = 1L,
                sessionId = 1L,
                packageName = "com.tencent.mm",
                startedAt = 1_000L,
                endedAt = 6_000L,
            ),
        )
        val samples = listOf(
            sample(1L, 1_000L, 80, temperatureC = 36.5f, currentMa = -400),
            sample(2L, 3_000L, 79, temperatureC = 37.5f, currentMa = -400),
            sample(3L, 5_000L, 78, temperatureC = 38.0f, currentMa = -400),
        )
        val rows = DischargeSessionMapper.buildAppUsageRows(
            usages = usages,
            samples = samples,
            sessionStartMs = sessionStart,
            sessionDurationMs = sessionDuration,
            uidPowers = emptyList(),
        )
        assertEquals(1, rows.size)
        assertEquals("com.tencent.mm", rows.single().packageName)
        assertEquals(5_000L, rows.single().durationMs)
        assertEquals(37.333f, rows.single().avgTemp, 0.01f)
        assertEquals(38.0f, rows.single().maxTemp, 0.01f)
        assertTrue(rows.single().usedMah > 0f)
    }

    @Test
    fun sceneRowsIgnoreUidPowerOnlyPackages() {
        val usages = listOf(
            BatteryAppUsageEntity(
                id = 1L,
                sessionId = 1L,
                packageName = "com.foreground.app",
                startedAt = 1_000L,
                endedAt = 4_000L,
            ),
        )
        val uidPowers = listOf(
            BatteryUidPowerEntity(
                sessionId = 1L,
                uid = 1000,
                packageName = "android",
                deltaMah = 80.0,
                capturedAt = 4_000L,
                updatedAt = 4_000L,
            ),
            BatteryUidPowerEntity(
                sessionId = 1L,
                uid = 10123,
                packageName = "com.foreground.app",
                deltaMah = 12.5,
                capturedAt = 4_000L,
                updatedAt = 4_000L,
            ),
        )
        val rows = DischargeSessionMapper.buildAppUsageRows(
            usages = usages,
            samples = emptyList(),
            sessionStartMs = 1_000L,
            sessionDurationMs = 3_000L,
            uidPowers = uidPowers,
        )
        assertEquals(listOf("com.foreground.app"), rows.map { it.packageName })
        assertEquals(3_000L, rows.single().durationMs)
        assertEquals(12.5f, rows.single().usedMah, 0.01f)
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

    private fun sample(
        id: Long,
        ts: Long,
        level: Int,
        temperatureC: Float? = null,
        currentMa: Int? = null,
    ) = BatteryRecordSampleEntity(
        id = id,
        sessionId = 1L,
        timestamp = ts,
        level = level,
        screenOn = true,
        temperatureC = temperatureC,
        currentMa = currentMa,
    )
}
