package io.github.lumkit.tweak.ui.screen.fpsRecord

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.base.LoadSlot
import io.github.lumkit.tweak.common.database.fps.repos.FpsRecordRepository
import io.github.lumkit.tweak.common.database.fps.table.FpsMetricEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsNoteSessionEntity
import io.github.lumkit.tweak.common.utils.AppThread
import io.github.lumkit.tweak.common.utils.AppsHelper
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.CpuCodenameUtils
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.CpuLoadUtils
import io.github.lumkit.tweak.common.utils.DeviceMemoryInfoUtils
import io.github.lumkit.tweak.common.utils.DeviceTemperatureUtils
import io.github.lumkit.tweak.common.utils.ForegroundAppMonitor
import io.github.lumkit.tweak.common.utils.GpuUtils
import io.github.lumkit.tweak.common.utils.SDK_INT
import io.github.lumkit.tweak.common.utils.SDK_RELEASE
import io.github.lumkit.tweak.common.utils.displayNameResource
import io.github.lumkit.tweak.common.utils.formatElapsedTimeInternal
import io.github.lumkit.tweak.common.utils.fps.FpsUtils
import io.github.lumkit.tweak.common.utils.getDeviceModel
import io.github.lumkit.tweak.common.utils.getDeviceScreenHeight
import io.github.lumkit.tweak.common.utils.getDeviceScreenWidth
import io.github.lumkit.tweak.common.utils.getDeviceType
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.packageName
import io.github.lumkit.tweak.common.utils.toastText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_record_fail_not_find_app_in_helper
import tweak_alpha.shared.generated.resources.text_record_fail_time_is_too_short
import tweak_alpha.shared.generated.resources.text_record_finished
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

object FpsRecordServiceViewModel : BaseViewModel() {

    private const val TAG = "FpsRecordServiceViewModel"
    private const val IMMERSIVE_IDLE_TIMEOUT_MILLIS = 5000L

    val startRecordSlot = LoadSlot()
    val stopRecordSlot = LoadSlot()

    private val repository: FpsRecordRepository = FpsRecordRepository()

    private val _isRecordingState = MutableStateFlow(false)
    val isRecordingState = _isRecordingState.asStateFlow()

    private val _elapsedTimeText = MutableStateFlow("00:00")
    val elapsedTimeText = _elapsedTimeText.asStateFlow()

    private val _isImmersiveMode = MutableStateFlow(false)
    val isImmersiveMode = _isImmersiveMode.asStateFlow()

    private var currentFps = 0f

    private val _isSamplingPaused = MutableStateFlow(false)
    val isSamplingPaused = _isSamplingPaused.asStateFlow()

    private var currentSessionId: Long? = null
    private var currentSessionStartTime: Long? = null
    private var currentSessionPackageName: String? = null
    private var currentRecordedDuration: Long = 0L
    private var sampleJob: Job? = null
    private var elapsedTimeJob: Job? = null
    private var immersiveModeJob: Job? = null
    private var isOverlayInteracting = false

    private val json by lazy {
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    fun startRecord() = suspendLaunch(
        slot = startRecordSlot,
        failed = {
            failure(it)
            withContext(Dispatchers.Main) {
                toastText(it.message ?: it.stackTraceToString())
            }
        }
    ) {
        loading()
        if (_isRecordingState.value) {
            success()
            return@suspendLaunch
        }
        ForegroundAppMonitor.refresh()
        // 创建session
        val currentForegroundPackage = ForegroundAppMonitor.currentForegroundPackage
        logD("currentForegroundPackage=$currentForegroundPackage", TAG)
        val currentAppInfo = AppsHelper.apps.value.find {
            it.packageName == currentForegroundPackage && it.packageName != packageName
        } ?: throw RuntimeException(getString(Res.string.text_record_fail_not_find_app_in_helper))

        cpuFrequencyUtil.resetCyclesCache()
        val sessionStartTime = Clock.System.now().toEpochMilliseconds()

        val session = FpsNoteSessionEntity(
            packageName = currentAppInfo.packageName,
            recordingTime = sessionStartTime,
            appName = currentAppInfo.appName,
            iconPath = currentAppInfo.iconPath,
            appVersion = "${currentAppInfo.versionName}(${currentAppInfo.versionCode})",
            deviceModel = getDeviceModel(),
            socJson = json.encodeToString(CpuCodenameUtils.getSocByCpuMode()),
            platformName = getString(getDeviceType().displayNameResource),
            platformVersionName = "Android $SDK_RELEASE($SDK_INT)",
            deviceScreenWidth = getDeviceScreenWidth(),
            deviceScreenHeight = getDeviceScreenHeight(),
            note = null,
        )
        val sessionId = repository.insertSession(
            session
        )

        logD("session id = $sessionId", TAG)

        currentSessionId = sessionId
        currentSessionStartTime = sessionStartTime
        currentSessionPackageName = currentAppInfo.packageName
        currentRecordedDuration = 0L
        setRecordingState(true)
        setSamplingPaused(false)
        _elapsedTimeText.value = formatElapsedTimeInternal(0)
        sampleJob?.cancel()
        elapsedTimeJob?.cancel()
        sampleJob = viewModelScope.launch {
            var nextSampleAt = Clock.System.now().toEpochMilliseconds() + 1000L
            var lastActiveAt = Clock.System.now().toEpochMilliseconds()
            while (isActive && _isRecordingState.value && currentSessionId == sessionId) {
                val waitMillis = (nextSampleAt - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
                delay(waitMillis.milliseconds)
                if (!isActive || !_isRecordingState.value || currentSessionId != sessionId) {
                    break
                }
                val sampleStartAt = Clock.System.now().toEpochMilliseconds()
                nextSampleAt += 1000L
                val sessionPackageName = currentSessionPackageName
                if (sessionPackageName.isNullOrEmpty()) {
                    setSamplingPaused(true)
                    lastActiveAt = sampleStartAt
                    continue
                }
                if (!cpuLoadUtils.isAppRunning(sessionPackageName)) {
                    logD("record app killed: $sessionPackageName", TAG)
                    stopRecord()
                    break
                }

                val fps = FpsUtils.getCurrentFps()
                currentFps = fps

                val currentForegroundPackage = ForegroundAppMonitor.currentForegroundPackage
                if (currentForegroundPackage != sessionPackageName) {
                    setSamplingPaused(true)
                    lastActiveAt = sampleStartAt
                    cpuFrequencyUtil.resetCyclesCache()
                    continue
                }
                setSamplingPaused(false)
                currentRecordedDuration += (sampleStartAt - lastActiveAt).coerceAtLeast(0L)
                lastActiveAt = sampleStartAt

                val metric = sampleMetric(
                    sessionId = sessionId,
                    recordingTime = currentRecordedDuration,
                    packageName = sessionPackageName,
                    fps = fps,
                )

                val sampleEndAt = Clock.System.now().toEpochMilliseconds()
                while (nextSampleAt <= sampleEndAt) {
                    nextSampleAt += 1000L
                }
                logD("sampleMetric time: ${sampleEndAt - sampleStartAt}ms", TAG)
                logD("metric data: $metric", TAG)

                repository.insertMetric(metric)
            }
        }
        elapsedTimeJob = viewModelScope.launch {
            while (isActive && _isRecordingState.value && currentSessionId == sessionId) {
                _elapsedTimeText.value = "FPS: %d\n%s".format(
                    currentFps.roundToInt(),
                    formatElapsedTimeInternal(currentRecordedDuration)
                )
                delay(200.milliseconds)
            }
        }
        success()
    }


    fun stopRecord() = suspendLaunch(
        slot = stopRecordSlot,
    ) {
        loading()

        if (!_isRecordingState.value) {
            success()
            return@suspendLaunch
        }

        val sessionId = currentSessionId
        val sessionStartTime = currentSessionStartTime
        sampleJob?.cancel()
        sampleJob = null
        elapsedTimeJob?.cancel()
        elapsedTimeJob = null

        setRecordingState(false)

        if (sessionId != null && sessionStartTime != null) {
            val duration = currentRecordedDuration
            if (duration < 1000) {
                repository.deleteSession(sessionId)
                withContext(Dispatchers.Main) {
                    toastText(getString(Res.string.text_record_fail_time_is_too_short))
                }
            } else {
                repository.finishSession(sessionId)
                withContext(Dispatchers.Main) {
                    toastText(getString(Res.string.text_record_finished))
                }
            }
        }

        currentSessionId = null
        currentSessionStartTime = null
        currentSessionPackageName = null
        currentRecordedDuration = 0L
        setSamplingPaused(false)
        _elapsedTimeText.value = formatElapsedTimeInternal(0)
        success()
    }

    fun onOverlayInteractionStart() {
        if (!_isRecordingState.value || isOverlayInteracting) {
            return
        }
        isOverlayInteracting = true
        updateOverlayImmersiveMode()
    }

    fun onOverlayInteractionEnd() {
        if (!_isRecordingState.value || !isOverlayInteracting) {
            return
        }
        isOverlayInteracting = false
        updateOverlayImmersiveMode()
    }

    private val cpuFrequencyUtil by lazy {
        CpuFrequencyUtil()
    }
    private val cpuLoadUtils by lazy {
        CpuLoadUtils()
    }

    private fun setRecordingState(isRecording: Boolean) {
        if (_isRecordingState.value == isRecording) {
            return
        }
        _isRecordingState.value = isRecording
        if (!isRecording) {
            isOverlayInteracting = false
        }
        updateOverlayImmersiveMode()
    }

    private fun setSamplingPaused(paused: Boolean) {
        if (_isSamplingPaused.value == paused) {
            return
        }
        _isSamplingPaused.value = paused
        updateOverlayImmersiveMode()
    }

    private fun updateOverlayImmersiveMode() {
        immersiveModeJob?.cancel()
        immersiveModeJob = null
        _isImmersiveMode.value = false

        val canEnterImmersiveMode = _isRecordingState.value &&
                !_isSamplingPaused.value &&
                !isOverlayInteracting
        if (!canEnterImmersiveMode) {
            return
        }

        immersiveModeJob = viewModelScope.launch {
            delay(IMMERSIVE_IDLE_TIMEOUT_MILLIS.milliseconds)
            if (_isRecordingState.value && !_isSamplingPaused.value && !isOverlayInteracting) {
                _isImmersiveMode.value = true
            }
        }
    }

    private suspend fun sampleMetric(
        sessionId: Long,
        recordingTime: Long,
        packageName: String,
        fps: Float,
    ): FpsMetricEntity = withContext(Dispatchers.IO) {
        coroutineScope {
            val timestamp = Clock.System.now().toEpochMilliseconds()

            val fpsDeferred = async {
                FpsSample(
                    fps = fps.toDouble(),
                    frameTime = if (fps > 0f) (1000f / fps).toInt() else 0,
                )
            }
            val cpuLoadDeferred = async { cpuLoadUtils.getCpuLoad() }
            val cpuStatsDeferred = async { sampleCpuStats() }
            val gpuDeferred = async { sampleGpu() }
            val batteryDeferred = async { sampleBattery() }
            val memoryFreqDeferred = async { DeviceMemoryInfoUtils.getMemoryFreq() }
            val coreTemperatureDeferred = async {
                (DeviceTemperatureUtils.getAverageCpuTemperature() ?: 25f).toDouble()
            }
            val threadLoadDeferred = async { AppThread.getThreadLoad(packageName) }

            val fpsSample = fpsDeferred.await()
            val cpuStats = cpuStatsDeferred.await()
            val gpuSample = gpuDeferred.await()
            val batterySample = batteryDeferred.await()

            FpsMetricEntity(
                sessionId = sessionId,
                timestamp = timestamp,
                fps = fpsSample.fps,
                batteryTemperature = batterySample.temperature,
                batteryLevel = batterySample.level,
                batteryCurrent = batterySample.current,
                batteryVoltage = batterySample.voltage,
                cpuLoad = json.encodeToString(cpuLoadDeferred.await()),
                cpuCurrentFreq = json.encodeToString(cpuStats.freq),
                cpuCurrentCycles = json.encodeToString(cpuStats.cycles),
                gpuLoad = gpuSample.load,
                gpuCurrentFreq = gpuSample.freq,
                frameTime = fpsSample.frameTime,
                memoryFreq = memoryFreqDeferred.await(),
                coreTemperature = coreTemperatureDeferred.await(),
                thread = json.encodeToString(threadLoadDeferred.await()),
                recordingTime = recordingTime,
            )
        }
    }

    private suspend fun sampleCpuStats(): CpuStatsSample {
        val cpuCount = cpuFrequencyUtil.getCoreCount()
        val cpuFreq = LongArray(cpuCount)
        val cpuCycles = LongArray(cpuCount)
        repeat(cpuCount) { coreIndex ->
            val coreName = "cpu$coreIndex"
            cpuFreq[coreIndex] = cpuFrequencyUtil.getCurrentFrequency(coreName).toLongOrNull() ?: 0L
            cpuCycles[coreIndex] = cpuFrequencyUtil.getCurrentCycles(coreIndex)
        }
        return CpuStatsSample(
            freq = cpuFreq.toIndexedMap(),
            cycles = cpuCycles.toIndexedMap(),
        )
    }

    private fun LongArray.toIndexedMap(): Map<Int, Long> {
        return buildMap(size) {
            forEachIndexed { index, value ->
                put(index, value)
            }
        }
    }

    private suspend fun sampleGpu(): GpuSample {
        val gpuFreq = GpuUtils.getGpuFreq().toLongOrNull()
        val canReadGpuInfo = gpuFreq != null
        val gpuLoad = GpuUtils.getGpuLoad().toDouble()
        return GpuSample(
            load = gpuLoad.takeIf { canReadGpuInfo },
            freq = gpuFreq.takeIf { canReadGpuInfo },
        )
    }

    private suspend fun sampleBattery(): BatterySample {
        return BatterySample(
            temperature = BatteryUtils.getTemperature()?.toDouble() ?: -1.0,
            level = BatteryUtils.getCapacityPercent()?.toDouble() ?: -1.0,
            current = BatteryUtils.getCurrent() ?: -1,
            voltage = BatteryUtils.getVoltage() ?: -1,
        )
    }

    @Serializable
    private data class FpsSample(
        val fps: Double,
        val frameTime: Int,
    )

    @Serializable
    private data class CpuStatsSample(
        val freq: Map<Int, Long>,
        val cycles: Map<Int, Long>,
    )

    @Serializable
    private data class GpuSample(
        val load: Double?,
        val freq: Long?,
    )

    @Serializable
    private data class BatterySample(
        val temperature: Double,
        val level: Double,
        val current: Int,
        val voltage: Int,
    )

}
