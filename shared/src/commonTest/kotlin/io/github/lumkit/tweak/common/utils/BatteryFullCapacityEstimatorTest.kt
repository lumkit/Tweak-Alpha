package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatteryFullCapacityEstimatorTest {

    @Test
    fun estimateMahAggregatesAllFinishedChargingSessions() {
        val estimated = BatteryFullCapacityEstimator.estimateMah(
            listOf(
                session(id = 1L) to listOf(
                    sample(sessionId = 1L, timestamp = 0L, level = 20, currentMa = 1000),
                    sample(sessionId = 1L, timestamp = 3_600_000L, level = 40, currentMa = 1000),
                ),
                session(id = 2L) to listOf(
                    sample(sessionId = 2L, timestamp = 0L, level = 40, currentMa = 500),
                    sample(sessionId = 2L, timestamp = 3_600_000L, level = 50, currentMa = 500),
                ),
            ),
        )

        assertEquals(5000, estimated)
    }

    @Test
    fun estimateMahIgnoresInvalidSessions() {
        val estimated = BatteryFullCapacityEstimator.estimateMah(
            listOf(
                session(id = 1L) to listOf(
                    sample(sessionId = 1L, timestamp = 0L, level = 20, currentMa = 1000),
                    sample(sessionId = 1L, timestamp = 3_600_000L, level = 40, currentMa = 1000),
                ),
                session(id = 2L, endedAt = null) to listOf(
                    sample(sessionId = 2L, timestamp = 0L, level = 50, currentMa = 1200),
                    sample(sessionId = 2L, timestamp = 3_600_000L, level = 80, currentMa = 1200),
                ),
                session(id = 3L, confirmed = false) to listOf(
                    sample(sessionId = 3L, timestamp = 0L, level = 30, currentMa = 900),
                    sample(sessionId = 3L, timestamp = 3_600_000L, level = 50, currentMa = 900),
                ),
            ),
        )

        assertEquals(5000, estimated)
    }

    @Test
    fun estimateMahReturnsNullWhenNoUsableHistoryExists() {
        val estimated = BatteryFullCapacityEstimator.estimateMah(
            listOf(
                session(id = 1L) to listOf(
                    sample(sessionId = 1L, timestamp = 0L, level = 30, currentMa = null),
                    sample(sessionId = 1L, timestamp = 3_600_000L, level = 30, currentMa = null),
                ),
            ),
        )

        assertNull(estimated)
    }

    private fun session(
        id: Long,
        endedAt: Long? = 10_000L,
        confirmed: Boolean = true,
        deleted: Boolean = false,
    ) = BatteryRecordSessionEntity(
        id = id,
        startedAt = 0L,
        endedAt = endedAt,
        intervalMs = 1_000,
        state = BatteryChargeState.CHARGING.code,
        confirmed = confirmed,
        deleted = deleted,
    )

    private fun sample(
        sessionId: Long,
        timestamp: Long,
        level: Int,
        currentMa: Int?,
    ) = BatteryRecordSampleEntity(
        sessionId = sessionId,
        timestamp = timestamp,
        level = level,
        currentMa = currentMa,
        screenOn = true,
    )
}
