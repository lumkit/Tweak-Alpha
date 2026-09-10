package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.CpuCodenameUtils
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.CpuLoadUtils
import io.github.lumkit.tweak.common.utils.DEFAULT_INFO_PAGE_CARD_KEYS
import io.github.lumkit.tweak.common.utils.DEFAULT_INFO_PAGE_ITEM_ORDER
import io.github.lumkit.tweak.common.utils.isInfoPageSectionVisible
import io.github.lumkit.tweak.common.utils.DeviceMemoryInfoUtils
import io.github.lumkit.tweak.common.utils.DeviceTemperatureUtils
import io.github.lumkit.tweak.common.utils.GpuUtils
import io.github.lumkit.tweak.common.utils.ProcessUtilLite
import io.github.lumkit.tweak.common.utils.StorageUtils
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.formatCurrent
import io.github.lumkit.tweak.common.utils.formatMemorySize
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.formatVoltage
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.model.AndroidSoc
import io.github.lumkit.tweak.model.GlobalViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_info_metric_gpu_memory
import tweak_alpha.shared.generated.resources.text_info_metric_load
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 判断应用是否处于前台
 */
expect fun isAppInForeground(): Boolean

object DeviceInfoViewModel : BaseViewModel() {

    private const val TAG = "DeviceInfoViewModel"

    @Serializable
    data class CpuInfoModel(
        val coreCluster: String,
        val coreLoad: Float? = null,
        val coreLoadText: String? = null,
        val coreTemperature: Float? = null,
        val coreTemperatureText: String? = null,
        val cpuStates: List<CoreInfoModel>,
        val soc: AndroidSoc?,
        val socName: String,
    )

    /**
     * [sampleId] 每个采样周期递增，保证 CpuCore 在 Debug/Release 下都会按周期重组绘制。
     */
    @Immutable
    @Serializable
    data class CoreInfoModel(
        val number: Int,
        val load: Float,
        val loadText: String,
        val currentFreq: String?,
        val minFreq: String?,
        val maxFreq: String?,
        val enabled: Boolean,
        val sampleId: Long = 0L,
    )

    @Immutable
    @Serializable
    data class MemoryInfoModel(
        val totalUsed: Float,
        val totalUsedText: String,
        val totalUsedSizeText: String,
        val totalSizeUnitText: String,
        val memoryUsed: Float,
        val memoryUsedText: String,
        val memoryUsedSizeText: String,
        val memoryAvailableSizeText: String,
        val memorySize: Long,
        val memorySizeUnitText: String,
        val swapUsed: Float,
        val swapUsedText: String,
        val swapUsedSizeText: String,
        val swapFreeSizeText: String,
        val swapSize: Long,
        val swapSizeUnitText: String,
        val swapCache: Long,
        val swapCacheUnitText: String,
    )

    @Immutable
    @Serializable
    data class GpuInfoModel(
        val load: Float?,
        val loadText: String?,
        val maxFreq: String?,
        val minFreq: String?,
        val freqRangeText: String?,
        val currentFreq: String?,
        val displayInfo: String?,
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
        val levelText: String?,
        val currentText: String?,
        val temperatureText: String?,
        val powerText: String?,
        val capacity: Float?,
        val capacityText: String?,
    )

    @Immutable
    @Serializable
    data class StorageInfoModel(
        val flashType: String,
        val usedLoad: Float,
        val totalText: String,
        val usedText: String,
        val usedTextInline: String,
        val userSpace: String,
        val freeText: String,
    )

    /**
     * 进程占用 TOP 项（供设备信息页展示）。
     * [iconPath] 仅 Android 应用进程有缓存路径，原生进程为空，UI 自行兜底图标。
     */
    @Immutable
    @Serializable
    data class TopProcessVo(
        val pid: Int,
        /**
         * 展示用名称：`应用标签:进程名`。
         * 例如进程名为 `com.android.chrome:root0` 时显示 `Chrome:root0`；
         * 无子进程后缀时为 `微信:com.tencent.mm`。
         */
        val name: String,
        /**
         * 用于跳转进程管理页并定位的包名。
         * Android 应用进程为去掉 `:xxx` 后的包名；原生进程为空。
         */
        val packageName: String,
        /** 原始进程名（包名或可执行名，可能含 `:子进程`） */
        val processName: String,
        /** 展示名：应用标签优先，否则 [processName] */
        val displayName: String,
        /** AppsHelper 图标缓存路径 */
        val iconPath: String,
        /** CPU 占用原始百分比（多核下可能 >100） */
        val cpu: Float,
        /** 0~1，供进度条；按 100% 归一并截断 */
        val cpuLoad: Float,
        val cpuText: String,
        val isAndroidProcess: Boolean,
    )

    val cpuFrequencyUtil = CpuFrequencyUtil()
    private val cpuLoadUtils = CpuLoadUtils()
    private val sampleIdGenerator = AtomicLong(0L)
    private var cachedSoc: AndroidSoc? = null
    private var cachedSocName: String? = null
    private var cachedClusterText: String? = null
    /** coreIndex -> (minFreqText, maxFreqText) */
    private val cachedCoreFreqRange = HashMap<Int, Pair<String?, String?>>()
    private var coreFreqRangeRefreshCountdown = 0

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

    private val _topProcessState = MutableStateFlow<List<TopProcessVo>>(emptyList())
    val topProcessState = _topProcessState.asStateFlow()

    private val _topProcessSupportState  = MutableStateFlow(false)
    val topProcessSupportState = _topProcessSupportState.asStateFlow()

    val enabledProcessInfo = TweakDataStore.infoPageEnabledProcessInfoFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /** 信息页卡片顺序，常驻 ViewModel，避免切页后从默认顺序再播一次重排动画。 */
    private val _sectionOrder = MutableStateFlow(DEFAULT_INFO_PAGE_ITEM_ORDER)
    val sectionOrder = _sectionOrder.asStateFlow()

    val enabledCards = TweakDataStore.infoPageEnabledCardsFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_INFO_PAGE_CARD_KEYS.toSet(),
        )

    /** 信息页列表滚动位置，随 ViewModel 常驻。 */
    val listState = LazyListState()

    fun moveSection(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }
        val current = _sectionOrder.value
        val visible = current.filter { isInfoPageSectionVisible(it, enabledCards.value) }
        if (fromIndex !in visible.indices || toIndex !in visible.indices) {
            return
        }
        val newVisible = visible.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        val visibleSet = visible.toSet()
        val visIter = newVisible.iterator()
        val newOrder = current.map { key ->
            if (key in visibleSet) visIter.next() else key
        }
        _sectionOrder.value = newOrder
        viewModelScope.launch {
            TweakDataStore.setInfoPageItemOrder(newOrder)
        }
    }

    init {
        viewModelScope.launch {
            TweakDataStore.infoPageItemOrderFlow()
                .distinctUntilChanged()
                .collect { order ->
                    if (order != _sectionOrder.value) {
                        _sectionOrder.value = order
                    }
                }
        }
        viewModelScope.launch(Dispatchers.Default) {
            _loadingState.value = false
            runCatching {
                withTimeout(5.seconds) {
                    _gpuSupported.value = GpuUtils.canReadGpuInfo()
                }
            }

            while (isActive) {
                // 后台时跳过采样，仅等待
                if (!isAppInForeground()) {
                    delay(1000.milliseconds)
                    continue
                }

                val tag = Clock.System.now().toEpochMilliseconds()
                try {
                    withTimeout(8.seconds) {
                        coroutineScope {
                            launch { updateCpuInfo() }
                            launch { updateMemoryInfo() }
                            launch { updateGpuInfo() }
                            launch { updateMoreInfo() }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    logE("device info sample timeout: ${e.message}", e, TAG)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logE("device info sample failed: ${e.message}", e, TAG)
                }

                val loadingTime = Clock.System.now().toEpochMilliseconds() - tag
                if (loadingTime > 200L) {
                    logD("loadingTime: $loadingTime", TAG)
                }

                _loadingState.value = true
                delay(GlobalViewModel.infoUpdateTimeSpanMillisecondsState.value.milliseconds)
            }
        }

        viewModelScope.launch {
            // 是否支持进程查询
            launch(Dispatchers.IO) {
                _topProcessSupportState.value = ProcessUtilLite.supported()
            }

            enabledProcessInfo.collect { enabled ->
                while (isActive && enabled) {
                    // 后台时跳过采样，仅等待
                    if (!isAppInForeground()) {
                        delay(1000.milliseconds)
                        continue
                    }

                    val tag = Clock.System.now().toEpochMilliseconds()
                    // 更新进程 TOP15
                    if (_topProcessSupportState.value) {
                        updateTopProcesses()
                    }

                    val loadingTime = Clock.System.now().toEpochMilliseconds() - tag
                    if (loadingTime > 200L) {
                        logD("process info loadingTime: $loadingTime", TAG)
                    }

                    delay(GlobalViewModel.processInfoUpdateTimeState.value.milliseconds)
                }
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
        val clusterTextDeferred = async {
            cachedClusterText ?: cpuFrequencyUtil.getClusterInfo().joinToString(separator = "+") {
                it.size.toString()
            }.also { cachedClusterText = it }
        }
        val socDeferred = async {
            cachedSoc ?: CpuCodenameUtils.getSocByCpuMode().also { cachedSoc = it }
        }
        val socNameDeferred = async {
            cachedSocName ?: (socDeferred.await()?.name ?: CpuCodenameUtils.getCpuCodename())
                .also { cachedSocName = it }
        }
        val cpuTemperatureDeferred =
            async { DeviceTemperatureUtils.getCpuCoreTemperature() }

        val coreCount = coreCountDeferred.await()
        val cpuLoad = cpuLoadDeferred.await()
        val refreshFreqRange = coreFreqRangeRefreshCountdown <= 0
        if (refreshFreqRange) {
            coreFreqRangeRefreshCountdown = 20
        } else {
            coreFreqRangeRefreshCountdown--
        }
        val sampleId = sampleIdGenerator.incrementAndGet()

        val cpuStates = (0 until coreCount)
            .map { coreIndex ->
                async {
                    val coreName = "cpu$coreIndex"
                    val currentFreqDeferred =
                        async { cpuFrequencyUtil.getCurrentFrequency(coreName) }
                    val onlineDeferred = async { cpuFrequencyUtil.getCoreOnlineState(coreIndex) }
                    val freqRangeDeferred = async {
                        val cached = cachedCoreFreqRange[coreIndex]
                        if (!refreshFreqRange && cached != null) {
                            cached
                        } else {
                            val minFreqDeferred =
                                async { cpuFrequencyUtil.getCurrentMinFrequency(coreName) }
                            val maxFreqDeferred =
                                async { cpuFrequencyUtil.getCurrentMaxFrequency(coreName) }
                            (formatFreqOrNull(minFreqDeferred.await(), "") to
                                formatFreqOrNull(maxFreqDeferred.await())).also {
                                cachedCoreFreqRange[coreIndex] = it
                            }
                        }
                    }

                    val load = cpuLoad[coreIndex]?.toFloat() ?: 0f
                    val freqRange = freqRangeDeferred.await()
                    val currentFreq = formatFreqOrNull(currentFreqDeferred.await())
                    CoreInfoModel(
                        number = coreIndex,
                        load = load.div(100f),
                        loadText = "%d%%".format(load.toInt()),
                        currentFreq = currentFreq,
                        minFreq = freqRange.first,
                        maxFreq = freqRange.second,
                        enabled = onlineDeferred.await(),
                        sampleId = sampleId,
                    )
                }
            }
            .awaitAll()

        val coreLoad = cpuLoad[-1]?.toFloat()
        val soc = socDeferred.await()
        val socTemperature = cpuTemperatureDeferred.await()
        val cpuInfoModel = CpuInfoModel(
            coreCluster = clusterTextDeferred.await(),
            coreLoad = coreLoad?.div(100f),
            coreLoadText = coreLoad?.let { "%d%%".format(it.toInt()) },
            coreTemperature = socTemperature,
            coreTemperatureText = socTemperature?.let { "%.1f°C".format(it) },
            cpuStates = cpuStates,
            soc = soc,
            socName = socNameDeferred.await(),
        )
        _cpuInfoState.value = cpuInfoModel
        cpuInfoListenerList.forEach {
            it(cpuInfoModel)
        }
    }

    fun formatFreq(freq: String, unit: String = "MHz"): String =
        formatFreqOrNull(freq, unit).orEmpty()

    fun formatFreqOrNull(freq: String, unit: String = "MHz"): String? {
        val khz = freq.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return "%d%s".format(khz / 1000L, unit)
    }

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
            totalUsedSizeText = totalUsedSize.formatMemorySize(" "),
            totalSizeUnitText = totalSize.formatMemorySize(" "),
            memoryUsed = ratioOf(memoryUsedSize, memorySize),
            memoryUsedText = percentText(memoryUsedSize, memorySize),
            memoryUsedSizeText = memoryUsedSize.formatMemorySize(" "),
            memoryAvailableSizeText = memoryInfo.memAvailable.formatMemorySize(" "),
            memorySize = memorySize,
            memorySizeUnitText = memorySize.formatMemorySize(" "),
            swapUsed = ratioOf(swapUsedSize, swapSize),
            swapUsedText = percentText(swapUsedSize, swapSize),
            swapUsedSizeText = swapUsedSize.formatMemorySize(" "),
            swapFreeSizeText = memoryInfo.swapFree.formatMemorySize(" "),
            swapSize = swapSize,
            swapSizeUnitText = swapSize.formatMemorySize(" "),
            swapCache = memoryInfo.swapCached,
            swapCacheUnitText = memoryInfo.swapCached.formatMemorySize(" "),
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
        val currentFreqMhz = GpuUtils.getGpuFreq().toLongOrNull()?.takeIf { it > 0L }
        val maxFreqMhz = GpuUtils.getMaxFreq().toLongOrNull()
            ?.let(GpuUtils::normalizeFrequencyToMhz)
            ?.takeIf { it > 0L }
        val minFreqMhz = GpuUtils.getMinFreq().toLongOrNull()
            ?.let(GpuUtils::normalizeFrequencyToMhz)
            ?.takeIf { it > 0L }
        val gpuLoad = GpuUtils.getGpuLoad().takeIf { it >= 0 }
        val memoryUsage = GpuUtils.getMemoryUsage()
        val loadPart = gpuLoad?.let {
            getString(Res.string.text_info_metric_load).format("%d%%".format(it))
        }
        val memoryPart = memoryUsage?.let {
            getString(Res.string.text_info_metric_gpu_memory).format(it)
        }
        val gpuLoadText = listOfNotNull(loadPart, memoryPart)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("     ")
        val display = GpuUtils.gles().takeIf { it.isNotBlank() }
        val freqRangeText = if (minFreqMhz != null && maxFreqMhz != null) {
            "(${minFreqMhz}~${maxFreqMhz}MHz)"
        } else {
            null
        }

        _gpuInfoState.value = GpuInfoModel(
            load = gpuLoad?.toFloat()?.div(100f),
            loadText = gpuLoadText,
            maxFreq = maxFreqMhz?.let { "${it}MHz" },
            minFreq = minFreqMhz?.let { "${it}MHz" },
            currentFreq = currentFreqMhz?.let { "${it}MHz" },
            displayInfo = display,
            memoryUsage = memoryUsage,
            freqRangeText = freqRangeText,
        )
    }

    private var flashType = ""
    private var totalSize: Long = 0L
    private suspend fun updateMoreInfo() {
        val batteryModel = run {
            val voltage = BatteryUtils.getVoltage()
            val current = BatteryUtils.getCurrent()
            val temperature = BatteryUtils.getTemperature()
            val power = BatteryUtils.getPower()
            val capacity = BatteryUtils.getCapacityPercent()

            BatteryInfoModel(
                levelText = voltage?.formatVoltage(),
                temperatureText = temperature?.let { "%.1f°C".format(it) },
                powerText = power?.formatPower(),
                capacity = capacity?.let { it.toFloat() / 100f },
                capacityText = capacity?.let { "%d%%".format(it) },
                currentText = current?.formatCurrent(),
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
                totalText = total.formatMemorySize(" "),
                usedText = used.formatMemorySize("\n"),
                usedTextInline = used.formatMemorySize(" "),
                userSpace = userSpace.joinToString("|"),
                freeText = free.formatMemorySize(" "),
            )
        }

        _moreInfoState.value = MoreInfoModel(
            battery = batteryModel,
            storage = storage,
        )
    }

    private fun CoroutineScope.updateTopProcesses() {
        launch(Dispatchers.IO) {
            _topProcessState.value = ProcessUtilLite.getAllProcess()
                .sortedByDescending { it.cpu }
                .take(15)
                .map { info ->
                    val cpu = info.cpu.coerceAtLeast(0f)
                    TopProcessVo(
                        pid = info.pid,
                        name = buildTopProcessName(info.displayName, info.name),
                        packageName = if (info.isAndroidProcess) info.appPackageName else "",
                        processName = info.name,
                        displayName = info.displayName,
                        iconPath = info.iconPath,
                        cpu = cpu,
                        cpuLoad = (cpu / 100f).coerceIn(0f, 1f),
                        cpuText = "%.1f%%".format(cpu),
                        isAndroidProcess = info.isAndroidProcess,
                    )
                }
        }
    }

    /**
     * 生成 `AppLabel:process` 形式。
     * - 有 `:子进程` 时只保留后缀（`Chrome:root0`）
     * - 否则为 `标签:完整进程名`（`微信:com.tencent.mm`）
     */
    private fun buildTopProcessName(displayName: String, processName: String): String {
        val process = processName.trim()
        val label = displayName.trim().ifBlank { process }
        if (process.isEmpty()) {
            return label
        }
        val processPart = process.substringAfter(':', missingDelimiterValue = process)
        if (label == process || label == processPart) {
            return label
        }
        return "$label:$processPart"
    }
}
