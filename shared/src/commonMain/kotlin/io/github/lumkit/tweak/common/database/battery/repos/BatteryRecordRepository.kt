package io.github.lumkit.tweak.common.database.battery.repos

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.BatteryPowerAggregate
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDatabase
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.utils.getDatabaseBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class BatteryRecordRepository {

    private val database get() = Companion.database

    companion object {
        private val database by lazy {
            getDatabaseBuilder<BatteryRecordDatabase>("battery_record.db").build()
        }
    }

    private val dao get() = database.recordDao()

    suspend fun insertSession(session: BatteryRecordSessionEntity): Long =
        dao.insertSession(session)

    /**
     * 开启新会话。放电默认 [confirmed]=true；充电默认未确认。
     */
    suspend fun startSession(
        state: BatteryChargeState,
        intervalMs: Int,
        startedAt: Long = Clock.System.now().toEpochMilliseconds(),
        confirmMs: Int = BatteryRecordDefaults.CONFIRM_MS,
    ): Long {
        return dao.insertSession(
            BatteryRecordSessionEntity(
                startedAt = startedAt,
                intervalMs = intervalMs,
                state = state.code,
                confirmed = state == BatteryChargeState.DISCHARGING,
                confirmMs = confirmMs,
                createdAt = startedAt,
            ),
        )
    }

    suspend fun updateSession(session: BatteryRecordSessionEntity) =
        dao.updateSession(session)

    suspend fun insertSample(sample: BatteryRecordSampleEntity): Long =
        dao.insertSample(sample)

    suspend fun insertSamples(samples: List<BatteryRecordSampleEntity>) =
        dao.insertSamples(samples)

    suspend fun querySessionById(sessionId: Long): BatteryRecordSessionEntity? =
        dao.querySessionById(sessionId)

    suspend fun queryActiveSession(): BatteryRecordSessionEntity? =
        dao.queryActiveSession()

    fun observeActiveSession(): Flow<BatteryRecordSessionEntity?> =
        dao.observeActiveSessions().map { it.firstOrNull() }

    fun queryConfirmedSessions(): Flow<List<BatteryRecordSessionEntity>> =
        dao.queryConfirmedSessions()

    fun observeLatestConfirmedChargingSession(): Flow<BatteryRecordSessionEntity?> =
        dao.observeLatestConfirmedSessionByState(BatteryChargeState.CHARGING.code)
            .map { it.firstOrNull() }

    fun querySessions(): Flow<List<BatteryRecordSessionEntity>> =
        dao.querySessions()

    suspend fun querySamplesBySessionId(sessionId: Long): List<BatteryRecordSampleEntity> =
        dao.querySamplesBySessionId(sessionId)

    fun observeSamplesBySessionId(sessionId: Long): Flow<List<BatteryRecordSampleEntity>> =
        dao.observeSamplesBySessionId(sessionId)

    fun observeLatestSampleBySessionId(sessionId: Long): Flow<BatteryRecordSampleEntity?> =
        dao.observeLatestSamplesBySessionId(sessionId).map { it.firstOrNull() }

    fun observePowerAggregateBySessionId(sessionId: Long): Flow<BatteryPowerAggregate> =
        dao.observePowerAggregateBySessionId(sessionId)

    suspend fun confirmSession(sessionId: Long) =
        dao.confirmSession(sessionId)

    suspend fun endSession(
        sessionId: Long,
        endedAt: Long = Clock.System.now().toEpochMilliseconds(),
    ) = dao.endSession(sessionId, endedAt)

    suspend fun softDeleteSession(sessionId: Long) =
        dao.softDeleteSession(sessionId)

    suspend fun softDeleteUnconfirmedSession(
        sessionId: Long,
        endedAt: Long = Clock.System.now().toEpochMilliseconds(),
    ) = dao.softDeleteUnconfirmedSession(sessionId, endedAt)

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSamplesBySessionId(sessionId)
        dao.deleteSession(sessionId)
    }
}
