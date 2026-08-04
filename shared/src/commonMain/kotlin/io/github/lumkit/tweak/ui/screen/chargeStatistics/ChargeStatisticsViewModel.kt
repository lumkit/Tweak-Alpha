package io.github.lumkit.tweak.ui.screen.chargeStatistics

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.ChartState
import io.github.lumkit.tweak.common.component.LintCurveChartState
import io.github.lumkit.tweak.common.component.bounds
import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.daemon.TweakDaemon
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.BatteryPowerAggregate
import io.github.lumkit.tweak.common.database.battery.BatteryRecordLogSync
import io.github.lumkit.tweak.common.database.battery.BatteryRecordLogWatcher
import io.github.lumkit.tweak.common.database.battery.repos.BatteryRecordRepository
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.utils.BatteryCapacityEstimateStore
import io.github.lumkit.tweak.common.utils.BatterySnapshot
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
import org.jetbrains.compose.resources.getString
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_charge_label_capacity_mah_format
import tweak_alpha.shared.generated.resources.text_charge_label_no_estimated_capacity
import tweak_alpha.shared.generated.resources.text_charge_label_wait_finish_for_estimation
import java.io.Closeable
import kotlin.math.abs
import kotlin.time.Clock

/**
 * 充电统计页数据：
 * - **图表 + 底部充电摘要**：默认绑定「最新充电会话」；可从充电记录点选覆盖
 * - **顶部实时标签 + 迷你曲线**：绑定「当前活跃会话」的最新采样（无活跃会话时清空实时项，不影响图表）
 */
class ChargeStatisticsViewModel : BaseViewModel() {

    companion object {
        const val NATIVE_DAEMON_ENABLE_LOAD_ID = "chargeStatisticsNativeDaemon"
    }

    private val repository = BatteryRecordRepository()

    // region 实时（当前活跃会话）

    private val _averagePower = MutableStateFlow(0L)
    val averagePower = _averagePower.asStateFlow()

    /** 瞬时功率（mW）；无有效采样时为 null */
    private val _currentPowerMw = MutableStateFlow<Int?>(null)
    val currentPowerMw = _currentPowerMw.asStateFlow()

    private val _currentMa = MutableStateFlow<Int?>(null)
    val currentMa = _currentMa.asStateFlow()

    private val _batteryTemperatureC = MutableStateFlow<Float?>(null)
    val batteryTemperatureC = _batteryTemperatureC.asStateFlow()

    private val _batteryVoltageMv = MutableStateFlow<Int?>(null)
    val batteryVoltageMv = _batteryVoltageMv.asStateFlow()

    private val _batterySnapshot = MutableStateFlow<BatterySnapshot?>(null)
    val batterySnapshot = _batterySnapshot.asStateFlow()

    private val _batteryFullCapacityText = MutableStateFlow<String?>(null)
    val batteryFullCapacityText = _batteryFullCapacityText.asStateFlow()

    private val _chargeState = MutableStateFlow<BatteryChargeState?>(null)
    val chargeState = _chargeState.asStateFlow()

    val chartState = LintCurveChartState(
        List(60) { ChartState(0f) }.map { (it.progress * 100f).bounds(0f, 100f) },
    )

    private var liveSessionId: Long? = null
    private var lastSparklineSampleId: Long = -1L

    // endregion

    // region 最新充电会话（图表 + 摘要）

    private val _chargingSessionSummary = MutableStateFlow<ChargingSessionSummary?>(null)
    val currentSessionSummary = _chargingSessionSummary.asStateFlow()

    private val _chartSamples = MutableStateFlow<List<ChargeChartSample>>(emptyList())
    val chartSamples = _chartSamples.asStateFlow()

    private val _currentTime = MutableStateFlow(Clock.System.now().toEpochMilliseconds())
    val currentTime = _currentTime.asStateFlow()

    // endregion

    private var chargingChartsJob: Job? = null
    private var liveSessionJob: Job? = null
    private var batteryLogWatcher: Closeable? = null

    private val _chartsSessionOverrideId = MutableStateFlow<Long?>(null)

    /** 主页面图表/摘要当前展示的充电 session（含历史点选覆盖） */
    private val _displayedChartsSessionId = MutableStateFlow<Long?>(null)
    val displayedChartsSessionId = _displayedChartsSessionId.asStateFlow()

    init {
        chartState.bindScope(viewModelScope)
        observeChargingSessionCharts()
        observeLiveActiveSession()
        observeBatteryFullCapacity()
        refreshBatterySnapshotOnce()
        startBatteryLogWatcher()
    }

    private fun observeBatteryFullCapacity() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                batterySnapshot,
                chargeState,
                TweakDataStore.estimatedBatteryFullCapacityMahFlow(),
            ) { snapshot, currentChargeState, estimated ->
                when {
                    snapshot?.currentFullCapacityMah != null -> {
                        getString(
                            Res.string.text_charge_label_capacity_mah_format,
                            snapshot.currentFullCapacityMah,
                        )
                    }
                    estimated != null && estimated > 0 -> {
                        getString(Res.string.text_charge_label_capacity_mah_format, estimated)
                    }
                    currentChargeState == BatteryChargeState.CHARGING -> {
                        getString(Res.string.text_charge_label_wait_finish_for_estimation)
                    }
                    else -> getString(Res.string.text_charge_label_no_estimated_capacity)
                }
            }.collect { text ->
                withContext(Dispatchers.Main.immediate) {
                    _batteryFullCapacityText.value = text
                }
            }
        }
    }

    private fun startBatteryLogWatcher() {
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

    override fun onCleared() {
        batteryLogWatcher?.close()
        batteryLogWatcher = null
        chargingChartsJob?.cancel()
        liveSessionJob?.cancel()
        historyObserveJob?.cancel()
        super.onCleared()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeChargingSessionCharts() {
        chargingChartsJob?.cancel()
        chargingChartsJob = viewModelScope.launch(Dispatchers.IO) {
            combine(
                _chartsSessionOverrideId,
                repository.observeChargingSessionForCharts(),
            ) { overrideId, defaultSession ->
                overrideId to defaultSession
            }
                .distinctUntilChanged()
                .flatMapLatest { (overrideId, defaultSession) ->
                    when {
                        overrideId != null -> {
                            combine(
                                repository.observeSessionById(overrideId)
                                    .distinctUntilChangedBy { session -> session?.id to session?.endedAt },
                                repository.observeSamplesBySessionId(overrideId),
                            ) { session, samples ->
                                ChargingSessionChartsUpdate(session, samples)
                            }
                        }
                        defaultSession != null -> {
                            combine(
                                repository.observeSessionById(defaultSession.id)
                                    .distinctUntilChangedBy { session -> session?.id to session?.endedAt },
                                repository.observeSamplesBySessionId(defaultSession.id),
                            ) { chargingSession, samples ->
                                ChargingSessionChartsUpdate(chargingSession, samples)
                            }
                        }
                        else -> flowOf(ChargingSessionChartsUpdate(null, emptyList()))
                    }
                }
                .collect { update ->
                    val liveActiveSessionId = repository.queryActiveSession()?.id
                    val summary = update.session?.let { session ->
                        ChargeSessionChartsMapper.buildSummary(
                            session = session,
                            samples = update.samples,
                            liveActiveSessionId = liveActiveSessionId,
                        )
                    }
                    val chartSamples = ChargeSessionChartsMapper.buildChartSamples(update.samples)
                    withContext(Dispatchers.Main.immediate) {
                        if (_chartsSessionOverrideId.value != null && update.session == null) {
                            _chartsSessionOverrideId.value = null
                        }
                        _displayedChartsSessionId.value = update.session?.id
                        _chargingSessionSummary.value = summary
                        _chartSamples.value = chartSamples
                        _currentTime.value = Clock.System.now().toEpochMilliseconds()
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLiveActiveSession() {
        liveSessionJob?.cancel()
        liveSessionJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeActiveSession()
                .distinctUntilChangedBy { session -> session?.id to session?.state }
                .flatMapLatest { session ->
                    logD("session = $session", "observeLiveActiveSession")
                    if (session == null) {
                        flowOf<LiveSessionUpdate?>(null)
                    } else {
                        combine(
                            flowOf(session),
                            repository.observeLatestSampleBySessionId(session.id),
                            repository.observePowerAggregateBySessionId(session.id),
                        ) { activeSession, latestSample, powerAggregate ->
                            LiveSessionUpdate(activeSession, latestSample, powerAggregate)
                        }
                    }
                }
                .collect { update ->
                    logD("update = $update", "observeLiveActiveSession")
                    withContext(Dispatchers.Main.immediate) {
                        _currentTime.value = Clock.System.now().toEpochMilliseconds()
                        if (update == null) {
                            clearLiveSessionState()
                            return@withContext
                        }
                        applyLiveSessionUpdate(update)
                    }
                }
        }
    }

    private fun clearLiveSessionState() {
        liveSessionId = null
        lastSparklineSampleId = -1L
        _averagePower.value = 0L
        _currentPowerMw.value = null
        _currentMa.value = null
        _batteryTemperatureC.value = null
        _batteryVoltageMv.value = null
        _chargeState.value = null
    }

    private fun applyLiveSessionUpdate(update: LiveSessionUpdate) {
        if (liveSessionId != update.session.id) {
            liveSessionId = update.session.id
            lastSparklineSampleId = -1L
        }
        _chargeState.value = update.session.chargeState
        _averagePower.value = update.powerAggregate.averagePowerUw

        val latest = update.latestSample ?: return
        _currentPowerMw.value = samplePowerUw(latest)?.let { (it / 1000L).toInt() }
        _currentMa.value = latest.currentMa
        _batteryTemperatureC.value = latest.temperatureC
        _batteryVoltageMv.value = latest.voltageMv

        if (latest.id <= lastSparklineSampleId) return
        lastSparklineSampleId = latest.id
        refreshBatterySnapshotOnce()

        pushSparklinePoint(update.powerAggregate.averagePowerUw, latest)
    }

    private fun pushSparklinePoint(averageUw: Long, latest: BatteryRecordSampleEntity) {
        val currentUw = samplePowerUw(latest) ?: 0L
        if (averageUw <= 0L) {
            chartState.push(0f)
            return
        }
        chartState.push((currentUw.toFloat() / averageUw.toFloat()).coerceIn(0f, 1f))
    }

    private fun refreshBatterySnapshotOnce() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = BatteryUtils.getSnapshot()
            withContext(Dispatchers.Main.immediate) {
                _batterySnapshot.value = snapshot
            }
        }
    }

    private data class ChargingSessionChartsUpdate(
        val session: BatteryRecordSessionEntity?,
        val samples: List<BatteryRecordSampleEntity>,
    )

    private data class LiveSessionUpdate(
        val session: BatteryRecordSessionEntity,
        val latestSample: BatteryRecordSampleEntity?,
        val powerAggregate: BatteryPowerAggregate,
    )

    /** 瞬时功率（µW）= |mA| × mV；缺电流或电压时返回 null */
    private fun samplePowerUw(sample: BatteryRecordSampleEntity): Long? {
        val currentMa = sample.currentMa ?: return null
        val voltageMv = sample.voltageMv ?: return null
        return abs(currentMa.toLong() * voltageMv.toLong())
    }

    data class ChargingSessionSummary(
        val startedAt: Long,
        val endedAt: Long,
        val isCurrentChargingSession: Boolean,
        val startLevel: Int,
        val endLevel: Int?,
        val energyGainUw: Long,
    )

    /** 与 [ChargingSessionSummary] 同构，供 UI 沿用 [currentSessionSummary] 命名 */
    typealias CurrentSessionSummary = ChargingSessionSummary

    data class ChargeChartSample(
        val elapsedMs: Long,
        val powerW: Float,
        val currentMa: Float,
        val level: Float,
        val temperatureC: Float,
    )

    // region 充电历史

    data class ChargeHistoryItem(
        val sessionId: Long,
        val startedAt: Long,
        val summary: ChargingSessionSummary,
    )

    data class ChargeHistoryUiState(
        val items: List<ChargeHistoryItem> = emptyList(),
        val selectedSessionIds: Set<Long> = emptySet(),
        val isSelectionMode: Boolean = false,
        val isLoading: Boolean = false,
        val isDeleting: Boolean = false,
    )

    private val _chargeHistoryUiState = MutableStateFlow(ChargeHistoryUiState())
    val chargeHistoryUiState = _chargeHistoryUiState.asStateFlow()

    /** null：尚未检测；true：需提示开启 Native Daemon；false：采样进程已运行 */
    private val _chargeHistoryNativeDaemonPrompt = MutableStateFlow<Boolean?>(null)
    val chargeHistoryNativeDaemonPrompt = _chargeHistoryNativeDaemonPrompt.asStateFlow()

    private var historyObserveJob: Job? = null

    /**
     * 进入/回到充电统计页时检测采样进程。
     * 每次都重新探测：关闭 Daemon 后再进入必须能再次弹窗（不能缓存 false）。
     */
    fun ensureNativeDaemonOnEnter() {
        viewModelScope.launch {
            _chargeHistoryNativeDaemonPrompt.value = needsNativeDaemonPrompt()
        }
    }

    fun dismissChargeHistoryNativeDaemonPrompt() {
        _chargeHistoryNativeDaemonPrompt.value = false
    }

    fun enableNativeDaemonForChargeHistory() = suspendLaunch(id = NATIVE_DAEMON_ENABLE_LOAD_ID) {
        loading()
        NativeDaemonController.setEnabled(true)
        if (!needsNativeDaemonPrompt()) {
            _chargeHistoryNativeDaemonPrompt.value = false
            success()
        } else {
            failure(IllegalStateException("native daemon not running"))
        }
    }

    /**
     * Release 下 stop 后 Binder 可能仍短暂可 ping；以 DataStore 开关为准，
     * 再辅以进程存活，避免关 Daemon 后不弹窗。
     */
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

    fun onChargeHistorySheetOpened() {
        if (historyObserveJob?.isActive == true) return
        _chargeHistoryUiState.update { it.copy(isLoading = it.items.isEmpty()) }
        observeChargeHistory()
    }

    fun onChargeHistorySheetClosed() {
        historyObserveJob?.cancel()
        historyObserveJob = null
        _chargeHistoryUiState.value = ChargeHistoryUiState()
    }

    fun selectChargeHistorySessionForCharts(sessionId: Long) {
        _chartsSessionOverrideId.value = sessionId
    }

    fun onChargeHistoryLongPress(sessionId: Long) {
        _chargeHistoryUiState.update { state ->
            state.copy(
                isSelectionMode = true,
                selectedSessionIds = state.selectedSessionIds + sessionId,
            )
        }
    }

    fun toggleChargeHistorySelection(sessionId: Long) {
        _chargeHistoryUiState.update { state ->
            val next = state.selectedSessionIds.toMutableSet()
            if (sessionId in next) next.remove(sessionId) else next.add(sessionId)
            state.copy(selectedSessionIds = next)
        }
    }

    fun exitChargeHistorySelectionMode() {
        _chargeHistoryUiState.update {
            it.copy(isSelectionMode = false, selectedSessionIds = emptySet())
        }
    }

    fun toggleChargeHistorySelectAll() {
        _chargeHistoryUiState.update { state ->
            val allIds = state.items.map { it.sessionId }.toSet()
            val selectAll = state.selectedSessionIds.size < allIds.size
            state.copy(
                selectedSessionIds = if (selectAll) allIds else emptySet(),
            )
        }
    }

    fun deleteSelectedChargeHistory() {
        val ids = _chargeHistoryUiState.value.selectedSessionIds
        if (ids.isEmpty() || _chargeHistoryUiState.value.isDeleting) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main.immediate) {
                _chargeHistoryUiState.update { it.copy(isDeleting = true) }
            }
            try {
                ids.forEach { sessionId ->
                    repository.deleteSession(sessionId)
                }
                BatteryCapacityEstimateStore.refreshFromChargingHistory(repository)
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    if (_chartsSessionOverrideId.value in ids) {
                        _chartsSessionOverrideId.value = null
                    }
                    _chargeHistoryUiState.update {
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
    private fun observeChargeHistory() {
        historyObserveJob?.cancel()
        historyObserveJob = viewModelScope.launch(Dispatchers.IO) {
            combine(
                repository.observeChargingSessions(),
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
                            sessionSamples.mapNotNull { (session, samples) ->
                                val summary = ChargeSessionChartsMapper.buildSummary(
                                    session = session,
                                    samples = samples,
                                    liveActiveSessionId = liveActiveSessionId,
                                ) ?: return@mapNotNull null
                                ChargeHistoryItem(
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
                        _chargeHistoryUiState.update { state ->
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
