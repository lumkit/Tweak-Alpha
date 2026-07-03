package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.CpuCodenameUtils
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.CpuLoadUtils
import io.github.lumkit.tweak.common.utils.DeviceMemoryInfoUtils
import io.github.lumkit.tweak.common.utils.DeviceTemperatureUtils
import io.github.lumkit.tweak.common.utils.GpuUtils
import io.github.lumkit.tweak.common.utils.StorageUtils
import io.github.lumkit.tweak.common.utils.formatCurrent
import io.github.lumkit.tweak.common.utils.formatMemorySize
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.formatVoltage
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.model.AndroidSoc
import io.github.lumkit.tweak.model.GlobalViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_gpu_load_format
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 判断应用是否处于前台
 */
expect fun isAppInForeground(): Boolean

object DeviceInfoViewModel : BaseViewModel() {

    @Serializable
    data class CpuInfoModel(
        val coreCluster: String,
        val coreLoad: Float? = null,
        val coreLoadText: String? = null,
        val coreTemperature: Float,
        val coreTemperatureText: String,
        val cpuStates: List<CoreInfoModel>,
        val soc: AndroidSoc?,
        val socName: String,
    )

    @OptIn(ExperimentalUuidApi::class)
    @Immutable
    @Serializable
    data class CoreInfoModel(
        val number: Int,
        val load: Float,
        val loadText: String,
        val currentFreq: String,
        val minFreq: String,
        val maxFreq: String,
        val enabled: Boolean,
        private val uuid: String = Uuid.random().toHexString()
    )

    @Immutable
    @Serializable
    data class MemoryInfoModel(
        val totalUsed: Float,
        val totalUsedText: String,
        val memoryUsed: Float,
        val memoryUsedText: String,
        val memorySize: Long,
        val memorySizeUnitText: String,
        val swapUsed: Float,
        val swapUsedText: String,
        val swapSize: Long,
        val swapSizeUnitText: String,
        val swapCache: Long,
        val swapCacheUnitText: String,
    )

    @Immutable
    @Serializable
    data class GpuInfoModel(
        val load: Float,
        val loadText: String,
        val maxFreq: String,
        val minFreq: String,
        val freqRangeText: String,
        val currentFreq: String,
        val displayInfo: String,
        val memoryUsage: String?,
    )

    @Immutable
    @Serializable
    data class MoreInfoModel(
        val battery: BatteryInfoModel,
        val storage: StorageInfoModel,
    )

    @Immutable
    @Serializable
    data class BatteryInfoModel(
        val levelText: String,
        val currentText: String,
        val temperatureText: String,
        val powerText: String,
        val capacity: Float,
        val capacityText: String,
    )

    @Immutable
    @Serializable
    data class StorageInfoModel(
        val flashType: String,
        val usedLoad: Float,
        val totalText: String,
        val usedText: String,
        val userSpace: String,
        val freeText: String,
    )

    val cpuFrequencyUtil = CpuFrequencyUtil()
    private val cpuLoadUtils = CpuLoadUtils()

    private val _loadingState = MutableStateFlow(false)
    val loadingState = _loadingState.asStateFlow()

    private val _cpuInfoState = MutableStateFlow<CpuInfoModel?>(null)
    val cpuInfoState = _cpuInfoState.asStateFlow()

    private val _memoryInfoState = MutableStateFlow<MemoryInfoModel?>(null)
    val memoryInfoState = _memoryInfoState.asStateFlow()

    private val _gpuSupported = MutableStateFlow(false)
    val gpuSupported = _gpuSupported.asStateFlow()

    private val _gpuInfoState = MutableStateFlow<GpuInfoModel?>(null)
    val gpuInfoState = _gpuInfoState.asStateFlow()

    private val _moreInfoState = MutableStateFlow<MoreInfoModel?>(null)
    val moreInfoState = _moreInfoState.asStateFlow()

    init {
        viewModelScope.launch {
            _loadingState.value = false
            // 初始化GPU是否支持
            _gpuSupported.value = GpuUtils.canReadGpuInfo()

            while (isActive) {
                // 后台时跳过采样，仅等待
                if (!isAppInForeground()) {
                    delay(1000.milliseconds)
                    continue
                }

                // 更新CPU信息
                val tag = Clock.System.now().toEpochMilliseconds()
                updateCpuInfo()
                // 更新内存信息
                updateMemoryInfo()
                // 更新GPU信息
                updateGpuInfo()
                // 更新更多信息
                updateMoreInfo()

                val loadingTime = Clock.System.now().toEpochMilliseconds() - tag
                logD("loadingTime: $loadingTime", "DeviceInfoViewModel")

                _loadingState.value = true
                delay(GlobalViewModel.infoUpdateTimeSpanMillisecondsState.value.milliseconds)
            }
        }
    }

    private val cpuInfoListenerList = CopyOnWriteArrayList<(CpuInfoModel) -> Unit>()

    fun addCpuInfoUpdateListener(listener: (CpuInfoModel) -> Unit) {
        cpuInfoListenerList.add(listener)
    }

    fun removeCpuInfoUpdateListener(listener: (CpuInfoModel) -> Unit) {
        cpuInfoListenerList.remove(listener)
    }

    private suspend fun updateCpuInfo() = coroutineScope {
        val coreCountDeferred = async { cpuFrequencyUtil.getCoreCount() }
        val cpuLoadDeferred = async { cpuLoadUtils.getCpuLoad() }
        val clusterInfoDeferred = async { cpuFrequencyUtil.getClusterInfo() }
        val socDeferred = async { CpuCodenameUtils.getSocByCpuMode() }
        val cpuTemperatureDeferred =
            async { DeviceTemperatureUtils.getAverageCpuTemperature() ?: 25f }

        val coreCount = coreCountDeferred.await()
        val cpuLoad = cpuLoadDeferred.await()
        val cpuStates = (0 until coreCount)
            .map { coreIndex ->
                async {
                    val coreName = "cpu$coreIndex"
                    val currentFreqDeferred =
                        async { cpuFrequencyUtil.getCurrentFrequency(coreName) }
                    val maxFreqDeferred =
                        async { cpuFrequencyUtil.getCurrentMaxFrequency(coreName) }
                    val minFreqDeferred =
                        async { cpuFrequencyUtil.getCurrentMinFrequency(coreName) }
                    val onlineDeferred = async { cpuFrequencyUtil.getCoreOnlineState(coreIndex) }

                    val load = cpuLoad[coreIndex]?.toFloat() ?: 0f
                    CoreInfoModel(
                        number = coreIndex,
                        load = load.div(100f),
                        loadText = "%d%%".format(load.toInt()),
                        currentFreq = formatFreq(currentFreqDeferred.await()),
                        minFreq = formatFreq(minFreqDeferred.await(), ""),
                        maxFreq = formatFreq(maxFreqDeferred.await()),
                        enabled = onlineDeferred.await(),
                    )
                }
            }
            .awaitAll()

        val clusterInfo = clusterInfoDeferred.await()
        val coreLoad = cpuLoad[-1]?.toFloat() ?: 0f
        val soc = socDeferred.await()
        val socTemperature = cpuTemperatureDeferred.await()
        val cpuInfoModel = CpuInfoModel(
            coreCluster = clusterInfo.joinToString(separator = "+") {
                it.size.toString()
            },
            coreLoad = coreLoad.div(100f),
            coreLoadText = "%d%%".format(coreLoad.toInt()),
            coreTemperature = socTemperature,
            coreTemperatureText = "%.1f°C".format(socTemperature),
            cpuStates = cpuStates,
            soc = soc,
            socName = soc?.name ?: CpuCodenameUtils.getCpuCodename()
        )
        _cpuInfoState.value = cpuInfoModel
        cpuInfoListenerList.forEach {
            it(cpuInfoModel)
        }
    }

    private fun formatFreq(freq: String, unit: String = "MHz"): String =
        "%d%s".format((freq.toLongOrNull() ?: 0L) / 1000L, unit)

    private suspend fun updateMemoryInfo() {
        val memoryInfo = DeviceMemoryInfoUtils.getMemoryInfo()
        val memorySize = memoryInfo.memTotal
        val memoryUsedSize = (memoryInfo.memTotal - memoryInfo.memAvailable).coerceAtLeast(0L)
        val swapSize = memoryInfo.swapTotal
        val swapUsedSize = (memoryInfo.swapTotal - memoryInfo.swapFree).coerceAtLeast(0L)
        val totalSize = memorySize + swapSize
        val totalUsedSize = memoryUsedSize + swapUsedSize

        _memoryInfoState.value = MemoryInfoModel(
            totalUsed = ratioOf(totalUsedSize, totalSize),
            totalUsedText = percentText(totalUsedSize, totalSize),
            memoryUsed = ratioOf(memoryUsedSize, memorySize),
            memoryUsedText = percentText(memoryUsedSize, memorySize),
            memorySize = memorySize,
            memorySizeUnitText = memorySize.formatMemorySize(),
            swapUsed = ratioOf(swapUsedSize, swapSize),
            swapUsedText = percentText(swapUsedSize, swapSize),
            swapSize = swapSize,
            swapSizeUnitText = swapSize.formatMemorySize(),
            swapCache = memoryInfo.swapCached,
            swapCacheUnitText = memoryInfo.swapCached.formatMemorySize(),
        )
    }

    private fun ratioOf(value: Long, total: Long): Float {
        if (total <= 0L) {
            return 0f
        }
        return (value.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun percentText(value: Long, total: Long): String {
        return "%d%%".format((ratioOf(value, total) * 100f).toInt())
    }

    private suspend fun updateGpuInfo() {
        val currentFreq = GpuUtils.getGpuFreq()
        val maxFreq = GpuUtils.getMaxFreq()
        val minFreq = GpuUtils.getMinFreq()
        val gpuLoad = GpuUtils.getGpuLoad()
        val memoryUsage = GpuUtils.getMemoryUsage()
        val gpuLoadText = getString(Res.string.text_gpu_load_format).format(
            "%d%%".format(gpuLoad),
            memoryUsage,
        )
        val display = GpuUtils.gles()

        val maxFreqFormat = GpuUtils.normalizeFrequencyToMhz(maxFreq.toLongOrNull() ?: 0)
        val minFreqFormat = GpuUtils.normalizeFrequencyToMhz(minFreq.toLongOrNull() ?: 0)
        _gpuInfoState.value = GpuInfoModel(
            load = gpuLoad.toFloat() / 100f,
            loadText = gpuLoadText,
            maxFreq = "${maxFreqFormat}MHz",
            minFreq = "${minFreqFormat}MHz",
            currentFreq = "${currentFreq}MHz",
            displayInfo = display,
            memoryUsage = memoryUsage,
            freqRangeText = "(${minFreqFormat}~${maxFreqFormat}MHz)"
        )
    }

    private var flashType = ""
    private var totalSize: Long = 0L
    private suspend fun updateMoreInfo() {
        val batteryModel = run {
            val level = BatteryUtils.getVoltage() ?: 0
            val current = BatteryUtils.getCurrent() ?: 0
            val temperature = BatteryUtils.getTemperature()
            val power = BatteryUtils.getPower() ?: 0
            val capacity = BatteryUtils.getCapacityPercent() ?: 0

            BatteryInfoModel(
                levelText = level.formatVoltage(),
                temperatureText = "%.1f°C".format(temperature),
                powerText = power.formatPower(),
                capacity = capacity.toFloat() / 100f,
                capacityText = "%d%%".format(capacity),
                currentText = current.formatCurrent(),
            )
        }

        val storage = run {
            val flashType = this.flashType.ifEmpty {
                val type = StorageUtils.getFlashType()
                this.flashType = type
                type
            }
            val total = run {
                if (totalSize <= 0L) {
                    totalSize = StorageUtils.getTotalBytes()
                }
                totalSize
            }
            val used = StorageUtils.getUsedBytes()
            val usedLoad = used.toFloat() / total.toFloat()
            val userSpace = StorageUtils.getUserProfiles()
            val free = StorageUtils.getFreeBytes()

            StorageInfoModel(
                flashType = flashType,
                usedLoad = usedLoad,
                totalText = total.formatMemorySize(),
                usedText = used.formatMemorySize("\n"),
                userSpace = userSpace.joinToString("|"),
                freeText = free.formatMemorySize(),
            )
        }

        _moreInfoState.value = MoreInfoModel(
            battery = batteryModel,
            storage = storage,
        )
    }
}
