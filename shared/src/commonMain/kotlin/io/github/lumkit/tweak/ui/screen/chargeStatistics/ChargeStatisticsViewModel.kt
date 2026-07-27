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
 * 充电统计：只订阅「最新一条采样 + SQL 功率聚合」，避免大 session 全量进内存。
 */
class ChargeStatisticsViewModel : BaseViewModel() {

    private val repository = BatteryRecordRepository()

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

    private val _currentSessionSummary = MutableStateFlow<CurrentSessionSummary?>(null)
    val currentSessionSummary = _currentSessionSummary.asStateFlow()

    private val _currentTime = MutableStateFlow(Clock.System.now().toEpochMilliseconds())
    val currentTime = _currentTime.asStateFlow()

    val chartState = LintCurveChartState(
        List(60) { ChartState(0f) }.map { (it.progress * 100f).bounds(0f, 100f) },
    )
    private var chartJob: Job? = null
    private var lastPushedSampleId: Long = -1L
    private var boundSessionId: Long? = null

    init {
        chartState.bindScope(viewModelScope)
        observeLatestChargingSessionSummary()
        observeActiveSessionSamples()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLatestChargingSessionSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeLatestConfirmedChargingSession()
                .distinctUntilChangedBy { it?.id to it?.endedAt }
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf<CurrentSessionSummary?>(null)
                    } else {
                        combine(
                            flowOf(session),
                            repository.querySessions(),
                            repository.observeSamplesBySessionId(session.id),
                            repository.observeLatestSampleBySessionId(session.id),
                        ) { currentSession, sessions, samples, latest ->
                            buildCurrentSessionSummary(
                                currentSession,
                                sessions.firstOrNull(),
                                samples,
                                latest,
                            )
                        }
                    }
                }
                .collect { summary ->
                    withContext(Dispatchers.Main.immediate) {
                        _currentSessionSummary.value = summary
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveSessionSamples() {
        chartJob?.cancel()
        chartJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeActiveSession()
                .distinctUntilChangedBy { it?.id to it?.state }
                .flatMapLatest { session ->
                    if (session == null) {
                        withContext(Dispatchers.Main.immediate) {
                            onSessionCleared()
                        }
                        flowOf<SessionSampleUpdate?>(null)
                    } else {
                        if (boundSessionId != session.id) {
                            boundSessionId = session.id
                            lastPushedSampleId = -1L
                        }
                        withContext(Dispatchers.Main.immediate) {
                            _chargeState.value = session.chargeState
                        }
                        combine(
                            flowOf(session),
                            repository.observeSamplesBySessionId(session.id),
                            repository.observeLatestSampleBySessionId(session.id),
                            repository.observePowerAggregateBySessionId(session.id),
                        ) { currentSession, samples, latest, aggregate ->
                            SessionSampleUpdate(currentSession, samples, latest, aggregate)
                        }
                    }
                }
                .collect { update ->
                    withContext(Dispatchers.Main.immediate) {
                        _currentTime.value = Clock.System.now().toEpochMilliseconds()
                        if (update == null) return@withContext
                        onSampleUpdate(update)
                    }
                }
        }
    }

    private fun onSessionCleared() {
        boundSessionId = null
        lastPushedSampleId = -1L
        _averagePower.value = 0L
        _currentPowerMw.value = null
        _currentMa.value = null
        _batteryTemperatureC.value = null
        _batteryVoltageMv.value = null
        _batterySnapshot.value = null
        _chargeState.value = null
        _currentSessionSummary.value = null
    }

    private fun onSampleUpdate(update: SessionSampleUpdate) {
        val averageUw = update.aggregate.averagePowerUw
        _averagePower.value = averageUw

        val latest = update.latest ?: return
        if (latest.id <= lastPushedSampleId) return

        val currentUw = samplePowerUw(latest)
        _currentPowerMw.value = currentUw?.let { (it / 1000L).toInt() }
        _currentMa.value = latest.currentMa
        _batteryTemperatureC.value = latest.temperatureC
        _batteryVoltageMv.value = latest.voltageMv
        lastPushedSampleId = latest.id

        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = BatteryUtils.getSnapshot()
            withContext(Dispatchers.Main.immediate) {
                _batterySnapshot.value = snapshot
            }
        }

        val current = currentUw ?: 0L
        if (averageUw <= 0L) {
            chartState.push(0f)
            return
        }
        chartState.push((current.toFloat() / averageUw.toFloat()).coerceIn(0f, 1f))
    }

    private fun buildCurrentSessionSummary(
        chargingSession: BatteryRecordSessionEntity,
        latestSession: BatteryRecordSessionEntity?,
        samples: List<BatteryRecordSampleEntity>,
        latest: BatteryRecordSampleEntity?,
    ): CurrentSessionSummary? {
        val firstSample = samples.firstOrNull() ?: return null
        val latestSample = latest ?: firstSample
        val isLatestSession = latestSession?.id == chargingSession.id
        val isCurrentChargingSession = isLatestSession && chargingSession.endedAt == null
        val endedAt = chargingSession.endedAt ?: latestSample.timestamp
        return CurrentSessionSummary(
            startedAt = chargingSession.startedAt,
            endedAt = endedAt,
            isCurrentChargingSession = isCurrentChargingSession,
            startLevel = firstSample.level,
            endLevel = samples.lastOrNull()?.level,
            energyGainUw = calculateEnergyGainUw(samples),
        )
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
            val durationHours = (current.timestamp - previous.timestamp).coerceAtLeast(0L) / 3_600_000.0
            val powerW = abs(previousCurrent.toDouble() * previousVoltage.toDouble()) / 1_000_000.0
            total += powerW * durationHours
        }
        return (total * 1_000_000.0).toLong()
    }

    /** 瞬时功率（µW）= |mA| × mV；缺电流或电压时返回 null */
    private fun samplePowerUw(sample: BatteryRecordSampleEntity): Long? {
        val currentMa = sample.currentMa ?: return null
        val voltageMv = sample.voltageMv ?: return null
        return abs(currentMa.toLong() * voltageMv.toLong())
    }

    override fun onCleared() {
        chartJob?.cancel()
    }

    private data class SessionSampleUpdate(
        val session: BatteryRecordSessionEntity,
        val samples: List<BatteryRecordSampleEntity>,
        val latest: BatteryRecordSampleEntity?,
        val aggregate: BatteryPowerAggregate,
    )

    data class CurrentSessionSummary(
        val startedAt: Long,
        val endedAt: Long,
        val isCurrentChargingSession: Boolean,
        val startLevel: Int,
        val endLevel: Int?,
        val energyGainUw: Long,
    )
}
