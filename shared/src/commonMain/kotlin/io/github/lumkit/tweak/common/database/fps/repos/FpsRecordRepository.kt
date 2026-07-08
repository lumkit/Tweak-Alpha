package io.github.lumkit.tweak.common.database.fps.repos

import io.github.lumkit.tweak.common.database.fps.FpsRecordDatabase
import io.github.lumkit.tweak.common.database.fps.table.FpsMetricEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsNoteSessionEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsSessionNote
import io.github.lumkit.tweak.common.utils.getDatabaseBuilder
import kotlinx.coroutines.flow.Flow

class FpsRecordRepository {

    private val database get() = Companion.database

    companion object {
        private val database by lazy {
            getDatabaseBuilder<FpsRecordDatabase>("fps_record.db").build()
        }
    }

    private val dao get() = database.recordDao()

    /** 创建FPS记录会话，返回自增主键ID */
    suspend fun insertSession(session: FpsNoteSessionEntity): Long {
        return dao.insertSession(session)
    }

    /** 记录单条FPS采样 */
    suspend fun insertMetric(metric: FpsMetricEntity) {
        dao.insertMetric(metric)
    }

    /** 批量记录FPS采样 */
    suspend fun insertMetrics(metrics: List<FpsMetricEntity>) {
        dao.insertMetrics(metrics)
    }

    /** 查询所有未删除的会话（响应式） */
    fun querySessions(): Flow<List<FpsNoteSessionEntity>> {
        return dao.querySessions()
    }

    fun querySessionNotes(): Flow<List<FpsSessionNote>> {
        return dao.querySessionNotes()
    }

    suspend fun finishSession(sessionId: Long) {
        dao.finishSession(sessionId)
    }

    /** 查询指定会话的采样记录 */
    suspend fun queryMetricsBySessionId(sessionId: Long): List<FpsMetricEntity> {
        return dao.queryMetricsBySessionId(sessionId)
    }

    /** 软删除会话 */
    suspend fun softDeleteSession(sessionId: Long) {
        dao.softDeleteSession(sessionId)
    }

    /** 硬删除会话及其关联采样数据 */
    suspend fun deleteSession(sessionId: Long) {
        dao.deleteMetricsBySessionId(sessionId)
        dao.deleteSession(sessionId)
    }
}
