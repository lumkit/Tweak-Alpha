package io.github.lumkit.tweak.common.database.fps.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.lumkit.tweak.common.database.fps.table.FpsMetricEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsNoteSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FpsRecordDao {

    /**
     * 创建FPS记录会话，返回自增主键ID
     */
    @Insert
    suspend fun insertSession(session: FpsNoteSessionEntity): Long

    /**
     * 记录FPS采样
     */
    @Insert
    suspend fun insertMetric(metric: FpsMetricEntity)

    /**
     * 批量记录FPS采样
     */
    @Insert
    suspend fun insertMetrics(metrics: List<FpsMetricEntity>)

    /**
     * 查询所有未删除的会话（按录制时间戳倒序）
     */
    @Query("SELECT * FROM fps_note_session WHERE `delete` = 0 ORDER BY recordingTime DESC")
    fun querySessions(): Flow<List<FpsNoteSessionEntity>>

    /**
     * 查询指定会话的采样记录（按时间戳升序）
     */
    @Query("SELECT * FROM fps_metric WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun queryMetricsBySessionId(sessionId: Long): List<FpsMetricEntity>

    /**
     * 软删除会话
     */
    @Query("UPDATE fps_note_session SET `delete` = 1 WHERE id = :sessionId")
    suspend fun softDeleteSession(sessionId: Long)

    /**
     * 删除会话及其关联的采样记录
     */
    @Query("DELETE FROM fps_metric WHERE sessionId = :sessionId")
    suspend fun deleteMetricsBySessionId(sessionId: Long)

    @Query("DELETE FROM fps_note_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}