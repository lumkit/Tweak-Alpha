package io.github.lumkit.tweak.ui.screen.chargeStatistics

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.ChartState
import io.github.lumkit.tweak.common.component.LintCurveChartState
import io.github.lumkit.tweak.common.component.bounds
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.BatteryPowerAggregate
import io.github.lumkit.tweak.common.database.battery.repos.BatteryRecordRepository
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.utils.BatterySnapshot
import io.github.lumkit.tweak.common.utils.BatteryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.time.Clock

/**
 * 充电统计页数据：
 * - **图表 + 底部充电摘要**：始终绑定「最新充电会话」（充电中为当前活跃充电 session，否则为历史上最近一条充电 session）
 * - **顶部实时标签 + 迷你曲线**：绑定「当前活跃会话」的最新采样（无活跃会话时清空实时项，不影响图表）
 */
class ChargeStatisticsViewModel : BaseViewModel() {

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

    init {
        chartState.bindScope(viewModelScope)
        observeChargingSessionCharts()
        observeLiveActiveSession()
        refreshBatterySnapshotOnce()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeChargingSessionCharts() {
        chargingChartsJob?.cancel()
        chargingChartsJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeChargingSessionForCharts()
                .distinctUntilChangedBy { session -> session?.id to session?.endedAt }
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(ChargingSessionChartsUpdate(null, emptyList()))
                    } else {
                        combine(
                            flowOf(session),
                            repository.observeSamplesBySessionId(session.id),
                        ) { chargingSession, samples ->
                            ChargingSessionChartsUpdate(chargingSession, samples)
                        }
                    }
                }
                .collect { update ->
                    val summary = update.session?.let { session ->
                        ChargeSessionChartsMapper.buildSummary(session, update.samples)
                    }
                    val chartSamples = ChargeSessionChartsMapper.buildChartSamples(update.samples)
                    withContext(Dispatchers.Main.immediate) {
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

    override fun onCleared() {
        chargingChartsJob?.cancel()
        liveSessionJob?.cancel()
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
}

/** 最新充电会话的摘要与曲线点（纯函数，便于单测） */
private object ChargeSessionChartsMapper {

    fun buildSummary(
        session: BatteryRecordSessionEntity,
        samples: List<BatteryRecordSampleEntity>,
    ): ChargeStatisticsViewModel.ChargingSessionSummary? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val isOngoingCharging =
            session.chargeState == BatteryChargeState.CHARGING && session.endedAt == null
        return ChargeStatisticsViewModel.ChargingSessionSummary(
            startedAt = session.startedAt,
            endedAt = session.endedAt ?: last.timestamp,
            isCurrentChargingSession = isOngoingCharging,
            startLevel = first.level,
            endLevel = last.level,
            energyGainUw = calculateEnergyGainUw(samples),
        )
    }

    fun buildChartSamples(samples: List<BatteryRecordSampleEntity>): List<ChargeStatisticsViewModel.ChargeChartSample> {
        if (samples.isEmpty()) return emptyList()
        val startAt = samples.first().timestamp
        return samples.map { sample ->
            val powerUw = samplePowerUw(sample)
            ChargeStatisticsViewModel.ChargeChartSample(
                elapsedMs = (sample.timestamp - startAt).coerceAtLeast(0L),
                powerW = powerUw?.let { it / 1_000_000f } ?: 0f,
                currentMa = sample.currentMa?.let { abs(it).toFloat() } ?: 0f,
                level = sample.level.toFloat(),
                temperatureC = sample.temperatureC ?: 0f,
            )
        }
    }

    private fun calculateEnergyGainUw(samples: List<BatteryRecordSampleEntity>): Long {
        if (samples.size < 2) return 0L
        var total = 0.0
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            val previousCurrent = previous.currentMa
            val previousVoltage = previous.voltageMv
            if (previousCurrent == null || previousVoltage == null) continue
            val durationHours =
                (current.timestamp - previous.timestamp).coerceAtLeast(0L) / 3_600_000.0
            val powerW = abs(previousCurrent.toDouble() * previousVoltage.toDouble()) / 1_000_000.0
            total += powerW * durationHours
        }
        return (total * 1_000_000.0).toLong()
    }

    private fun samplePowerUw(sample: BatteryRecordSampleEntity): Long? {
        val currentMa = sample.currentMa ?: return null
        val voltageMv = sample.voltageMv ?: return null
        return abs(currentMa.toLong() * voltageMv.toLong())
    }
}
