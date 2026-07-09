package io.github.lumkit.tweak.common.database.fps.repos

import io.github.lumkit.tweak.common.database.fps.FpsRecordDatabase
import io.github.lumkit.tweak.common.database.fps.table.CpuLoadRecord
import io.github.lumkit.tweak.common.database.fps.table.FpsMetricEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsNoteSessionEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsRecordDetailAggregate
import io.github.lumkit.tweak.common.database.fps.table.FpsSessionNote
import io.github.lumkit.tweak.common.utils.formatDateTime
import io.github.lumkit.tweak.common.utils.getDatabaseBuilder
import io.github.lumkit.tweak.model.AndroidSoc
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class FpsRecordRepository {

    private val database get() = Companion.database

    companion object {
        private val database by lazy {
            getDatabaseBuilder<FpsRecordDatabase>("fps_record.db").build()
        }

        private val json by lazy {
            Json {
                ignoreUnknownKeys = true
            }
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

    suspend fun queryRecordDetail(sessionId: Long): FpsRecordDetailAggregate? {
        val session = dao.querySessionById(sessionId) ?: return null
        val metrics = dao.queryMetricsBySessionId(sessionId)
        return buildRecordDetail(session, metrics)
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

    private fun buildRecordDetail(
        session: FpsNoteSessionEntity,
        metrics: List<FpsMetricEntity>,
    ): FpsRecordDetailAggregate {
        val fpsSamples = metrics.map { it.fps }
        val batteryTemperatureSamples = metrics.map { it.batteryTemperature }
        val cpuLoadSamples = metrics.map { metric ->
            decodeOrNull<CpuLoadRecord>(metric.cpuLoad) ?: CpuLoadRecord()
        }
        val gpuLoadSamples = metrics.mapNotNull { it.gpuLoad }
        val frameTimeSamples = metrics.map { it.frameTime }
        val cpuFreqSamples = metrics.map { metric ->
            decodeOrNull<Map<Int, Long>>(metric.cpuCurrentFreq).orEmpty()
        }
        val cpuTemperatureSamples = metrics.map { it.coreTemperature }
        val cpuCyclesSamples = metrics.map { metric ->
            decodeOrNull<Map<Int, Long>>(metric.cpuCurrentCycles).orEmpty()
        }
        val gpuFreqSamples = metrics.mapNotNull { it.gpuCurrentFreq }
        val ddrFreqSamples = metrics.mapNotNull { it.memoryFreq }
        val powerSamples = metrics.map { it.batteryCurrent * it.batteryVoltage / 1000.0 }
        val batteryLevelSamples = metrics.map { it.batteryLevel }
        val threadLoadSamples = metrics.map { metric ->
            decodeOrNull<Map<String, Double>>(metric.thread).orEmpty()
        }
        val maxFps = fpsSamples.maxOrNull() ?: 0.0
        val minFps = fpsSamples.minOrNull() ?: 0.0
        val avgFps = fpsSamples.averageOrZero()
        return FpsRecordDetailAggregate(
            sessionId = session.id,
            recordDate = formatDateTime(session.recordingTime),
            platformName = session.platformName,
            deviceSoc = decodeOrNull<AndroidSoc>(session.socJson),
            deviceModel = session.deviceModel,
            platformVersionName = session.platformVersionName,
            appName = session.appName,
            appIconPath = session.iconPath,
            appVersion = session.appVersion,
            appPackageName = session.packageName,
            deviceResolution = "${session.deviceScreenWidth}x${session.deviceScreenHeight}",
            maxFps = maxFps,
            minFps = minFps,
            avgFps = avgFps,
            fpsDiff = maxFps - minFps,
            above45FpsRatio = if (fpsSamples.isEmpty()) 0.0 else fpsSamples.count { it >= 45.0 }.toDouble() / fpsSamples.size,
            low5Fps = fpsSamples.sorted().let { sorted ->
                if (sorted.isEmpty()) {
                    0.0
                } else {
                    val count = (sorted.size * 0.05).toInt().coerceAtLeast(1)
                    sorted.take(count).averageOrZero()
                }
            },
            maxBatteryTemperature = batteryTemperatureSamples.maxOrNull() ?: 0.0,
            avgPower = powerSamples.averageOrZero(),
            fpsSamples = fpsSamples,
            batteryTemperatureSamples = batteryTemperatureSamples,
            cpuLoadSamples = cpuLoadSamples,
            gpuLoadSamples = gpuLoadSamples,
            frameTimeSamples = frameTimeSamples,
            cpuFreqSamples = cpuFreqSamples,
            cpuTemperatureSamples = cpuTemperatureSamples,
            cpuCyclesSamples = cpuCyclesSamples,
            gpuFreqSamples = gpuFreqSamples,
            ddrFreqSamples = ddrFreqSamples,
            powerSamples = powerSamples,
            batteryLevelSamples = batteryLevelSamples,
            threadLoadSamples = threadLoadSamples,
        )
    }

    private fun List<Double>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    private inline fun <reified T> decodeOrNull(value: String?): T? {
        if (value.isNullOrBlank()) {
            return null
        }
        return runCatching {
            json.decodeFromString<T>(value)
        }.getOrNull()
    }
}
