package io.github.lumkit.tweak.ui.screen.info

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.CpuCodenameUtils
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.CpuLoadUtils
import io.github.lumkit.tweak.common.utils.DeviceTemperatureUtils
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    val cpuFrequencyUtil = CpuFrequencyUtil()
    private val cpuLoadUtils = CpuLoadUtils()

    private val _loadingState = MutableStateFlow(false)
    val loadingState = _loadingState.asStateFlow()

    private val _cpuInfoState = MutableStateFlow<CpuInfoModel?>(null)
    val cpuInfoState = _cpuInfoState.asStateFlow()

    init {
        viewModelScope.launch {
            _loadingState.value = false
            while (isActive) {
                // 更新CPU信息
                val tag = Clock.System.now().toEpochMilliseconds()
                updateCpuInfo()

                println("load time: ${Clock.System.now().toEpochMilliseconds() - tag}ms")
                _loadingState.value = true
                delay(GlobalViewModel.infoUpdateTimeSpanState.value.milliseconds)
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
        val cpuTemperatureDeferred = async { DeviceTemperatureUtils.getAverageCpuTemperature() ?: 25f }

        val coreCount = coreCountDeferred.await()
        val cpuLoad = cpuLoadDeferred.await()
        val cpuStates = (0 until coreCount)
            .map { coreIndex ->
                async {
                    val coreName = "cpu$coreIndex"
                    val currentFreqDeferred = async { cpuFrequencyUtil.getCurrentFrequency(coreName) }
                    val maxFreqDeferred = async { cpuFrequencyUtil.getCurrentMaxFrequency(coreName) }
                    val minFreqDeferred = async { cpuFrequencyUtil.getCurrentMinFrequency(coreName) }
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

    private fun formatFreq(freq: String, unit: String = "MHz"): String = "%d%s".format((freq.toLongOrNull() ?: 0L) / 1000L, unit)
}
