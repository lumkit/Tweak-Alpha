package io.github.lumkit.tweak.common.database.battery.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.lumkit.tweak.common.database.battery.BatteryPowerAggregate
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryRecordDao {

    @Insert
    suspend fun insertSession(session: BatteryRecordSessionEntity): Long

    @Update
    suspend fun updateSession(session: BatteryRecordSessionEntity)

    @Insert
    suspend fun insertSample(sample: BatteryRecordSampleEntity): Long

    @Insert
    suspend fun insertSamples(samples: List<BatteryRecordSampleEntity>)

    @Query("SELECT * FROM battery_record_session WHERE id = :sessionId LIMIT 1")
    suspend fun querySessionById(sessionId: Long): BatteryRecordSessionEntity?

    /** 当前未结束且未删除的会话（至多一条业务上应存在） */
    @Query(
        """
        SELECT * FROM battery_record_session
        WHERE deleted = 0 AND endedAt IS NULL
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun queryActiveSession(): BatteryRecordSessionEntity?

    /** 观察当前活跃会话（0 或 1 条） */
    @Query(
        """
        SELECT * FROM battery_record_session
        WHERE deleted = 0 AND endedAt IS NULL
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    fun observeActiveSessions(): Flow<List<BatteryRecordSessionEntity>>

    @Query(
        """
        SELECT * FROM battery_record_session
        WHERE deleted = 0 AND confirmed = 1
        ORDER BY startedAt DESC
        """,
    )
    fun queryConfirmedSessions(): Flow<List<BatteryRecordSessionEntity>>

    @Query(
        """
        SELECT * FROM battery_record_session
        WHERE deleted = 0 AND confirmed = 1 AND state = :state
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    fun observeLatestConfirmedSessionByState(state: Int): Flow<List<BatteryRecordSessionEntity>>

    /** 最近一条充电会话（含未结束、未确认），按 [startedAt] 倒序 */
    @Query(
        """
        SELECT * FROM battery_record_session
        WHERE deleted = 0 AND state = :state
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    fun observeLatestChargingSessions(state: Int): Flow<List<BatteryRecordSessionEntity>>

    @Query(
        """
        SELECT * FROM battery_record_session
        WHERE deleted = 0
        ORDER BY startedAt DESC
        """,
    )
    fun querySessions(): Flow<List<BatteryRecordSessionEntity>>

    @Query(
        """
        SELECT * FROM battery_record_sample
        WHERE sessionId = :sessionId
        ORDER BY timestamp ASC
        """,
    )
    suspend fun querySamplesBySessionId(sessionId: Long): List<BatteryRecordSampleEntity>

    @Query(
        """
        SELECT * FROM battery_record_sample
        WHERE sessionId = :sessionId
        ORDER BY timestamp ASC
        """,
    )
    fun observeSamplesBySessionId(sessionId: Long): Flow<List<BatteryRecordSampleEntity>>

    /** 仅观察该会话最新一条采样（按自增 id） */
    @Query(
        """
        SELECT * FROM battery_record_sample
        WHERE sessionId = :sessionId
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    fun observeLatestSamplesBySessionId(sessionId: Long): Flow<List<BatteryRecordSampleEntity>>

    /**
     * 会话功率 SQL 聚合：Σ|mA×mV| 与有效样本数。
     * 表变更时由 SQLite 重算，不把全量行映射到 Kotlin。
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(ABS(currentMa * voltageMv)), 0) AS powerSumUw,
            COUNT(*) AS sampleCount
        FROM battery_record_sample
        WHERE sessionId = :sessionId
          AND currentMa IS NOT NULL
          AND voltageMv IS NOT NULL
        """,
    )
    fun observePowerAggregateBySessionId(sessionId: Long): Flow<BatteryPowerAggregate>

    @Query(
        """
        UPDATE battery_record_session
        SET confirmed = 1
        WHERE id = :sessionId
        """,
    )
    suspend fun confirmSession(sessionId: Long)

    @Query(
        """
        UPDATE battery_record_session
        SET endedAt = :endedAt
        WHERE id = :sessionId AND endedAt IS NULL
        """,
    )
    suspend fun endSession(sessionId: Long, endedAt: Long)

    @Query(
        """
        UPDATE battery_record_session
        SET deleted = 1
        WHERE id = :sessionId
        """,
    )
    suspend fun softDeleteSession(sessionId: Long)

    /** 软删除未确认的充电会话（确认窗口内状态回退时使用） */
    @Query(
        """
        UPDATE battery_record_session
        SET deleted = 1, endedAt = :endedAt
        WHERE id = :sessionId AND confirmed = 0
        """,
    )
    suspend fun softDeleteUnconfirmedSession(sessionId: Long, endedAt: Long)

    @Query("DELETE FROM battery_record_sample WHERE sessionId = :sessionId")
    suspend fun deleteSamplesBySessionId(sessionId: Long)

    @Query("DELETE FROM battery_record_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
