package io.github.lumkit.tweak.ui.screen.fpsRecord

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.database.fps.repos.FpsRecordRepository
import io.github.lumkit.tweak.common.database.fps.table.FpsMetricEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsNoteSessionEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsSessionNote
import io.github.lumkit.tweak.common.utils.SDK_INT
import io.github.lumkit.tweak.common.utils.SDK_RELEASE
import io.github.lumkit.tweak.common.utils.displayNameResource
import io.github.lumkit.tweak.common.utils.formatDateTime
import io.github.lumkit.tweak.common.utils.formatElapsedTime
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.getDeviceModel
import io.github.lumkit.tweak.common.utils.getDeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds

object FpsRecordViewModel : BaseViewModel() {

    private const val ID_CREATE_SESSION = "create_session"
    private const val ID_RECORD_METRIC = "record_metric"
    private const val ID_RECORD_METRICS = "record_metrics"
    private const val ID_LOAD_METRICS = "load_metrics"
    private const val ID_SOFT_DELETE = "soft_delete_session"
    private const val ID_DELETE = "delete_session"

    private val repository: FpsRecordRepository = FpsRecordRepository()

    @Serializable
    @Immutable
    data class PlatformInfo(
        val platformName: String,
        val deviceModel: String,
        val platformVersionName: String,
    )

    @Serializable
    @Immutable
    data class FpsSessionNoteVo(
        val sessionId: Long,
        val appName: String,
        val appPackageName: String,
        val appIconPath: String?,
        val createTime: String,
        val recordingDuration: String,
        val avgFps: String,
        val avgPower: String,
    )

    private val _platformInfoState = MutableStateFlow<PlatformInfo?>(null)
    val platformInfoState = _platformInfoState.asStateFlow()

    val sessionNotes = repository.querySessionNotes()
        .map { notes ->
            notes.map { it.toVo() }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    /** 当前选中会话的采样数据 */
    private val _metrics = MutableStateFlow<List<FpsMetricEntity>>(emptyList())
    val metrics = _metrics.asStateFlow()

    private val selectableSessionIds = sessionNotes
        .map { notes ->
            notes.map { it.sessionId }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val _selectedSessionIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSessionIds = combine(_selectedSessionIds, selectableSessionIds) { selected, selectable ->
        val selectableSet = selectable.toSet()
        selected.filter { it in selectableSet }.sorted()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val hasSelectedAllSessions = combine(selectedSessionIds, selectableSessionIds) { selected, selectable ->
        selectable.isNotEmpty() && selected.size == selectable.size
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    init {
        suspendLaunch(id = "init_platform_info") {
            loading()
            val platformName = getString(getDeviceType().displayNameResource)
            _platformInfoState.value = PlatformInfo(
                platformName = platformName,
                deviceModel = getDeviceModel(),
                platformVersionName = "Android ${SDK_RELEASE}($SDK_INT)"
            )
            success()
        }

        viewModelScope.launch {
            selectableSessionIds.collect { selectable ->
                val selectableSet = selectable.toSet()
                _selectedSessionIds.update { selected ->
                    selected.filter { it in selectableSet }.toSet()
                }
            }
        }
    }

    /** 创建会话 */
    fun createSession(session: FpsNoteSessionEntity, onCreated: (Long) -> Unit = {}) {
        suspendLaunch(id = ID_CREATE_SESSION, context = Dispatchers.IO) {
            loading()
            val id = repository.insertSession(session)
            success()
            onCreated(id)
        }
    }

    /** 记录单条采样 */
    fun recordMetric(metric: FpsMetricEntity) {
        suspendLaunch(id = ID_RECORD_METRIC, context = Dispatchers.IO) {
            repository.insertMetric(metric)
        }
    }

    /** 批量记录采样 */
    fun recordMetrics(metrics: List<FpsMetricEntity>) {
        suspendLaunch(id = ID_RECORD_METRICS, context = Dispatchers.IO) {
            repository.insertMetrics(metrics)
        }
    }

    /** 加载指定会话的采样数据 */
    fun loadMetrics(sessionId: Long) {
        suspendLaunch(id = ID_LOAD_METRICS, context = Dispatchers.IO) {
            loading()
            _metrics.value = repository.queryMetricsBySessionId(sessionId)
            success()
        }
    }

    /** 软删除会话 */
    fun softDeleteSession(sessionId: Long) {
        suspendLaunch(id = ID_SOFT_DELETE, context = Dispatchers.IO) {
            loading()
            repository.softDeleteSession(sessionId)
            success()
        }
    }

    /** 硬删除会话及采样数据 */
    fun deleteSession(sessionId: Long) {
        suspendLaunch(id = ID_DELETE, context = Dispatchers.IO) {
            loading()
            repository.deleteSession(sessionId)
            success()
        }
    }

    fun toggleSelectedSession(id: Long) {
        if (id !in selectableSessionIds.value) {
            return
        }
        _selectedSessionIds.update { selected ->
            if (id in selected) selected - id else selected + id
        }
    }

    fun toggleSelectedAllSession() {
        val selectable = selectableSessionIds.value.toSet()
        if (selectable.isEmpty()) {
            clearSelectedSessions()
            return
        }
        _selectedSessionIds.update { selected ->
            val current = selected.intersect(selectable)
            if (current.size == selectable.size) {
                emptySet()
            } else {
                selectable
            }
        }
    }

    fun clearSelectedSessions() {
        _selectedSessionIds.value = emptySet()
    }

    fun softDeleteSessions() = suspendLaunch(
        "softDeleteSessions"
    ) {
        loading()
        _selectedSessionIds.value.forEach {
            delay(50.milliseconds)
            repository.softDeleteSession(it)
        }
        success()
    }
}

private fun FpsSessionNote.toVo(): FpsRecordViewModel.FpsSessionNoteVo {
    return FpsRecordViewModel.FpsSessionNoteVo(
        sessionId = sessionId,
        appName = appName,
        appPackageName = appPackageName,
        appIconPath = appIconPath,
        createTime = formatDateTime(createTime),
        avgFps = "%.2f FPS".format(avgFps),
        avgPower = avgPower.formatPower(),
        recordingDuration = recordingDuration.formatElapsedTime()
    )
}

expect fun showRecordOverlay()

expect fun hideRecordOverlay()
