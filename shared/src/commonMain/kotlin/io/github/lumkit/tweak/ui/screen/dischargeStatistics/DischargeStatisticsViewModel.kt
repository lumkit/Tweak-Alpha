package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.AppMinuteBarColumn
import io.github.lumkit.tweak.common.component.LineChartData
import io.github.lumkit.tweak.common.component.LineChartXAxisData
import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.daemon.TweakDaemon
import io.github.lumkit.tweak.common.database.battery.BatteryRecordLogSync
import io.github.lumkit.tweak.common.database.battery.BatteryRecordLogWatcher
import io.github.lumkit.tweak.common.database.battery.repos.BatteryRecordRepository
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryUidPowerEntity
import io.github.lumkit.tweak.common.utils.AppsHelper
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.time.Clock

class DischargeStatisticsViewModel : BaseViewModel() {

    companion object {
        const val NATIVE_DAEMON_ENABLE_LOAD_ID = "dischargeStatisticsNativeDaemon"
        private const val TAG = "DischargeStatisticsViewModel"
    }

    private val repository = BatteryRecordRepository()

    enum class AppSortMode {
        Duration,
        UsedMah,
    }

    enum class AppPowerListStatus {
        Collecting,
        Empty,
        Ready,
    }

    data class DischargeSessionSummary(
        val startedAt: Long,
        val endedAt: Long,
        val isCurrentDischargingSession: Boolean,
        val startLevel: Int,
        val endLevel: Int,
        val energyUw: Long,
        val averagePowerUw: Long,
    )

    data class BatteryInfoRow(
        val energyUw: Long,
        val usedMah: Float,
        val temperatureC: Float?,
        val voltageMv: Int?,
    )

    data class AppUsageRow(
        val packageName: String,
        val appName: String,
        val iconPath: String?,
        val usedMah: Float,
        val avgTemp: Float,
        val maxTemp: Float,
        val durationMs: Long,
    )

    data class DischargeHistoryItem(
        val sessionId: Long,
        val startedAt: Long,
        val summary: DischargeSessionSummary,
    )

    data class DischargeHistoryUiState(
        val items: List<DischargeHistoryItem> = emptyList(),
        val selectedSessionIds: Set<Long> = emptySet(),
        val isSelectionMode: Boolean = false,
        val isLoading: Boolean = false,
        val isDeleting: Boolean = false,
    )

    private val _summary = MutableStateFlow<DischargeSessionSummary?>(null)
    val summary = _summary.asStateFlow()

    private val _levelSeries = MutableStateFlow<LineChartData?>(null)
    val levelSeries = _levelSeries.asStateFlow()

    private val _levelXAxis = MutableStateFlow(LineChartXAxisData(emptyList()))
    val levelXAxis = _levelXAxis.asStateFlow()

    private val _appMinuteColumns = MutableStateFlow<List<AppMinuteBarColumn>>(emptyList())
    val appMinuteColumns = _appMinuteColumns.asStateFlow()

    private val _chartIconSlotCount = MutableStateFlow(30)
    val chartIconSlotCount = _chartIconSlotCount.asStateFlow()

    private val _chartXTickIndexes = MutableStateFlow<List<Int>>(emptyList())
    val chartXTickIndexes = _chartXTickIndexes.asStateFlow()

    private val _batteryInfoRow = MutableStateFlow<BatteryInfoRow?>(null)
    val batteryInfoRow = _batteryInfoRow.asStateFlow()

    private val _appRows = MutableStateFlow<List<AppUsageRow>>(emptyList())
    val appRows = _appRows.asStateFlow()

    private val _appPowerListStatus = MutableStateFlow(AppPowerListStatus.Empty)
    val appPowerListStatus = _appPowerListStatus.asStateFlow()

    private val _etaText = MutableStateFlow("--")
    val etaText = _etaText.asStateFlow()

    private val _screenOnDurationMs = MutableStateFlow<Long?>(null)
    val screenOnDurationMs = _screenOnDurationMs.asStateFlow()

    private val _currentLevel = MutableStateFlow<Int?>(null)
    val currentLevel = _currentLevel.asStateFlow()

    private val _currentTime = MutableStateFlow(Clock.System.now().toEpochMilliseconds())
    val currentTime = _currentTime.asStateFlow()

    private val _hasChartSamples = MutableStateFlow(false)
    val hasChartSamples = _hasChartSamples.asStateFlow()

    private val _appSortMode = MutableStateFlow(AppSortMode.Duration)
    val appSortMode = _appSortMode.asStateFlow()

    private val _chartsSessionOverrideId = MutableStateFlow<Long?>(null)

    private val _displayedChartsSessionId = MutableStateFlow<Long?>(null)
    val displayedChartsSessionId = _displayedChartsSessionId.asStateFlow()

    private val _dischargeHistoryUiState = MutableStateFlow(DischargeHistoryUiState())
    val dischargeHistoryUiState = _dischargeHistoryUiState.asStateFlow()

    private val _nativeDaemonPrompt = MutableStateFlow<Boolean?>(null)
    val nativeDaemonPrompt = _nativeDaemonPrompt.asStateFlow()

    private var chartsJob: Job? = null
    private var historyObserveJob: Job? = null
    private var batteryLogWatcher: Closeable? = null

    private var rawAppRows: List<AppUsageRow> = emptyList()
    private var lastEtaMs: Long? = null
    private var lastEtaSessionId: Long? = null

    fun startBatteryLogWatcher() {
        logD("监听文件变化", TAG)
        batteryLogWatcher?.close()
        batteryLogWatcher = BatteryRecordLogWatcher.start(
            onCreated = { path -> BatteryRecordLogSync.syncOnLogCreated(path) },
            onAppended = { path -> BatteryRecordLogSync.syncOnLogAppended(path) },
            onRemoved = { path -> BatteryRecordLogSync.syncOnLogRemoved(path) },
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { BatteryRecordLogSync.syncActiveSessionLogs() }
        }
    }

    fun release() {
        logD("释放监听", TAG)
        batteryLogWatcher?.close()
        batteryLogWatcher = null
        chartsJob?.cancel()
        historyObserveJob?.cancel()
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }

    fun setAppSortMode(mode: AppSortMode) {
        _appSortMode.value = mode
        _appRows.value = sortAppRows(rawAppRows, mode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDischargingSessionCharts() {
        chartsJob?.cancel()
        chartsJob = viewModelScope.launch(Dispatchers.IO) {
            logD("订阅耗电记录", TAG)
            combine(
                _chartsSessionOverrideId,
                repository.observeDischargingSessionForCharts(),
            ) { overrideId, defaultSession ->
                overrideId to defaultSession
            }
                .distinctUntilChanged()
                .flatMapLatest { (overrideId, defaultSession) ->
                    when {
                        overrideId != null -> {
                            combine(
                                repository.observeSessionById(overrideId)
                                    .distinctUntilChangedBy { it?.id to it?.endedAt },
                                repository.observeSamplesBySessionId(overrideId),
                                repository.observeAppUsagesBySessionId(overrideId),
                                repository.observeUidPowersBySessionId(overrideId),
                            ) { session, samples, usages, uidPowers ->
                                DischargeChartsUpdate(session, samples, usages, uidPowers)
                            }
                        }
                        defaultSession != null -> {
                            combine(
                                repository.observeSessionById(defaultSession.id)
                                    .distinctUntilChangedBy { it?.id to it?.endedAt },
                                repository.observeSamplesBySessionId(defaultSession.id),
                                repository.observeAppUsagesBySessionId(defaultSession.id),
                                repository.observeUidPowersBySessionId(defaultSession.id),
                            ) { session, samples, usages, uidPowers ->
                                DischargeChartsUpdate(session, samples, usages, uidPowers)
                            }
                        }
                        else -> flowOf(DischargeChartsUpdate(null, emptyList(), emptyList(), emptyList()))
                    }
                }
                .combine(AppsHelper.apps) { update, _ -> update }
                .collect { update ->
                    applyChartsUpdate(update)
                }
        }
    }

    private suspend fun applyChartsUpdate(update: DischargeChartsUpdate) {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val session = update.session
        val samples = update.samples
        val usages = update.usages
        val uidPowers = update.uidPowers

        val liveActiveSessionId = repository.queryActiveSession()?.id
        val summary = session?.let {
            DischargeSessionMapper.buildSummary(
                session = it,
                samples = samples,
                nowMs = nowMs,
                liveActiveSessionId = liveActiveSessionId,
            )
        }
        val screenOnDurationMs = session?.let {
            DischargeSessionMapper.calculateScreenOnDurationMs(
                session = it,
                samples = samples,
                nowMs = nowMs,
                liveActiveSessionId = liveActiveSessionId,
            )
        }
        val levelChart = if (session != null) {
            DischargeSessionMapper.buildLevelChart(
                session = session,
                samples = samples,
                seriesName = "电量",
                color = Color(0xFF4CAF50).copy(alpha = 0.75f),
                nowMs = nowMs,
            )
        } else {
            null
        }
        val axisScale = levelChart?.axisScale
            ?: session?.let {
                val end = it.endedAt ?: samples.lastOrNull()?.timestamp ?: nowMs
                DischargeSessionMapper.resolveAxisScale((end - it.startedAt).coerceAtLeast(0L))
            }
        val minuteColumns = if (session != null && axisScale != null) {
            DischargeSessionMapper.buildAppMinuteColumns(
                session = session,
                samples = samples,
                usages = usages,
                nowMs = nowMs,
                axisScale = axisScale,
            )
        } else {
            emptyList()
        }
        val sessionDurationMs = summary?.let {
            (it.endedAt - it.startedAt).coerceAtLeast(0L)
        } ?: 0L
        val appRows = DischargeSessionMapper.buildAppUsageRows(
            usages = usages,
            samples = samples,
            sessionStartMs = summary?.startedAt ?: session?.startedAt ?: 0L,
            sessionDurationMs = sessionDurationMs,
            uidPowers = uidPowers,
        )
        val listStatus = when {
            appRows.isNotEmpty() -> AppPowerListStatus.Ready
            session != null &&
                session.endedAt == null &&
                session.id == liveActiveSessionId -> AppPowerListStatus.Collecting
            else -> AppPowerListStatus.Empty
        }
        val infoRow = summary?.let {
            DischargeSessionMapper.buildBatteryInfoRow(samples, it.energyUw)
        }
        val estimatedCapacity = TweakDataStore.estimatedBatteryFullCapacityMahFlow()
            .first()
            ?.takeIf { it > 0 }
        val capacity = estimatedCapacity
            ?: BatteryUtils.getCurrentFullCapacity()
            ?: BatteryUtils.getDesignCapacity()
        val sessionId = session?.id
        if (sessionId != lastEtaSessionId) {
            lastEtaMs = null
            lastEtaSessionId = sessionId
        }
        val etaMs = if (summary != null && samples.size >= 2) {
            estimateDischargeEtaMs(
                DischargeEtaInput(
                    samples = samples,
                    capacityMah = capacity,
                    remainingLevel = summary.endLevel,
                    isLiveSession = summary.isCurrentDischargingSession,
                    previousEtaMs = lastEtaMs,
                ),
            )
        } else {
            null
        }
        lastEtaMs = etaMs

        withContext(Dispatchers.Main.immediate) {
            if (_chartsSessionOverrideId.value != null && session == null) {
                _chartsSessionOverrideId.value = null
            }
            _displayedChartsSessionId.value = session?.id
            _summary.value = summary
            _batteryInfoRow.value = infoRow
            _appMinuteColumns.value = minuteColumns
            _chartIconSlotCount.value = axisScale?.iconSlotCount ?: 30
            _chartXTickIndexes.value = levelChart?.xTickIndexes.orEmpty()
            rawAppRows = appRows
            _appRows.value = sortAppRows(appRows, _appSortMode.value)
            _appPowerListStatus.value = listStatus
            _etaText.value = formatDischargeEtaText(etaMs)
            _screenOnDurationMs.value = screenOnDurationMs
            _currentLevel.value = samples.lastOrNull()?.level
            _currentTime.value = nowMs
            _hasChartSamples.value = samples.isNotEmpty()
            if (levelChart != null) {
                _levelXAxis.value = levelChart.xAxis
                _levelSeries.value = levelChart.series
            } else {
                _levelXAxis.value = LineChartXAxisData(emptyList())
                _levelSeries.value = null
            }
        }
    }

    private fun sortAppRows(rows: List<AppUsageRow>, mode: AppSortMode): List<AppUsageRow> {
        return when (mode) {
            AppSortMode.Duration -> rows.sortedByDescending { it.durationMs }
            AppSortMode.UsedMah -> rows.sortedByDescending { it.usedMah }
        }
    }

    private data class DischargeChartsUpdate(
        val session: BatteryRecordSessionEntity?,
        val samples: List<BatteryRecordSampleEntity>,
        val usages: List<BatteryAppUsageEntity>,
        val uidPowers: List<BatteryUidPowerEntity>,
    )

    // region 历史

    fun ensureNativeDaemonOnEnter() {
        viewModelScope.launch {
            _nativeDaemonPrompt.value = needsNativeDaemonPrompt()
        }
    }

    fun dismissNativeDaemonPrompt() {
        _nativeDaemonPrompt.value = false
    }

    fun enableNativeDaemon() = suspendLaunch(id = NATIVE_DAEMON_ENABLE_LOAD_ID) {
        loading()
        NativeDaemonController.setEnabled(true)
        if (!needsNativeDaemonPrompt()) {
            _nativeDaemonPrompt.value = false
            success()
        } else {
            failure(IllegalStateException("native daemon not running"))
        }
    }

    private suspend fun needsNativeDaemonPrompt(): Boolean {
        val enabled = TweakDataStore.nativeDaemonEnabledFlow().first()
        if (!enabled) return true
        return !isBatterySamplerRunning()
    }

    private suspend fun isBatterySamplerRunning(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            TweakDaemon.ping() || TweakDaemon.isRunning()
        }.getOrDefault(false)
    }

    fun onHistorySheetOpened() {
        if (historyObserveJob?.isActive == true) return
        _dischargeHistoryUiState.update { it.copy(isLoading = it.items.isEmpty()) }
        observeDischargeHistory()
    }

    fun onHistorySheetClosed() {
        historyObserveJob?.cancel()
        historyObserveJob = null
        _dischargeHistoryUiState.value = DischargeHistoryUiState()
    }

    fun selectHistorySessionForCharts(sessionId: Long) {
        _chartsSessionOverrideId.value = sessionId
    }

    fun onHistoryLongPress(sessionId: Long) {
        _dischargeHistoryUiState.update { state ->
            state.copy(
                isSelectionMode = true,
                selectedSessionIds = state.selectedSessionIds + sessionId,
            )
        }
    }

    fun toggleHistorySelection(sessionId: Long) {
        _dischargeHistoryUiState.update { state ->
            val next = state.selectedSessionIds.toMutableSet()
            if (sessionId in next) next.remove(sessionId) else next.add(sessionId)
            state.copy(selectedSessionIds = next)
        }
    }

    fun exitHistorySelectionMode() {
        _dischargeHistoryUiState.update {
            it.copy(isSelectionMode = false, selectedSessionIds = emptySet())
        }
    }

    fun toggleHistorySelectAll() {
        _dischargeHistoryUiState.update { state ->
            val allIds = state.items.map { it.sessionId }.toSet()
            val selectAll = state.selectedSessionIds.size < allIds.size
            state.copy(
                selectedSessionIds = if (selectAll) allIds else emptySet(),
            )
        }
    }

    fun deleteSelectedDischargeHistory() {
        val ids = _dischargeHistoryUiState.value.selectedSessionIds
        if (ids.isEmpty() || _dischargeHistoryUiState.value.isDeleting) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main.immediate) {
                _dischargeHistoryUiState.update { it.copy(isDeleting = true) }
            }
            try {
                ids.forEach { sessionId ->
                    repository.deleteSession(sessionId)
                }
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    if (_chartsSessionOverrideId.value in ids) {
                        _chartsSessionOverrideId.value = null
                    }
                    _dischargeHistoryUiState.update {
                        it.copy(
                            isDeleting = false,
                            isSelectionMode = false,
                            selectedSessionIds = emptySet(),
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDischargeHistory() {
        historyObserveJob?.cancel()
        historyObserveJob = viewModelScope.launch(Dispatchers.IO) {
            combine(
                repository.observeDischargingSessions(),
                repository.observeActiveSession().map { it?.id }.distinctUntilChanged(),
            ) { sessions, liveActiveSessionId ->
                sessions to liveActiveSessionId
            }
                .distinctUntilChangedBy { (sessions, liveId) ->
                    sessions.map { it.id } to liveId
                }
                .flatMapLatest { (sessions, liveActiveSessionId) ->
                    if (sessions.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            sessions.map { session ->
                                repository.observeSamplesBySessionId(session.id)
                                    .map { samples -> session to samples }
                            },
                        ) { sessionSamples ->
                            val nowMs = Clock.System.now().toEpochMilliseconds()
                            sessionSamples.mapNotNull { (session, samples) ->
                                val summary = DischargeSessionMapper.buildSummary(
                                    session = session,
                                    samples = samples,
                                    nowMs = nowMs,
                                    liveActiveSessionId = liveActiveSessionId,
                                ) ?: return@mapNotNull null
                                DischargeHistoryItem(
                                    sessionId = session.id,
                                    startedAt = session.startedAt,
                                    summary = summary,
                                )
                            }
                        }
                    }
                }
                .collect { items ->
                    val itemIds = items.map { it.sessionId }.toSet()
                    withContext(Dispatchers.Main.immediate) {
                        _dischargeHistoryUiState.update { state ->
                            state.copy(
                                items = items,
                                isLoading = false,
                                selectedSessionIds = state.selectedSessionIds intersect itemIds,
                            )
                        }
                    }
                }
        }
    }

    // endregion
}
