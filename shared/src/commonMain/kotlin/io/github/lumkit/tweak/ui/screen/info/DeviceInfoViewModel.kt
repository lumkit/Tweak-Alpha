package io.github.lumkit.tweak.ui.screen.info

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.CpuCodenameUtils
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.CpuLoadUtils
import io.github.lumkit.tweak.model.AndroidSoc
import io.github.lumkit.tweak.model.GlobalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

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

    @Serializable
    data class CoreInfoModel(
        val number: Int,
        val load: Float,
        val loadText: String,
        val currentFreq: String,
        val minFreq: String,
        val maxFreq: String,
        val enabled: Boolean,
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
                updateCpuInfo()

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

    private suspend fun updateCpuInfo() {
        val coreCount = cpuFrequencyUtil.getCoreCount()
        val cpuStates = mutableListOf<CoreInfoModel>()
        val cpuLoad = cpuLoadUtils.getCpuLoad()
        val clusterInfo = cpuFrequencyUtil.getClusterInfo()

        for (i in 0 until coreCount) {
            val coreName = "cpu$i"
            val currentFreq = formatFreq(cpuFrequencyUtil.getCurrentFrequency(coreName))
            val maxFreq = formatFreq(cpuFrequencyUtil.getCurrentMaxFrequency(coreName))
            val minFreq = formatFreq(cpuFrequencyUtil.getCurrentMinFrequency(coreName), "")
            val online = cpuFrequencyUtil.getCoreOnlineState(i)

            val load = cpuLoad[i]?.toFloat() ?: 0f
            cpuStates.add(
                CoreInfoModel(
                    number = i,
                    load = load.div(100f),
                    loadText = "%d%%".format(load.toInt()),
                    currentFreq = currentFreq,
                    minFreq = minFreq,
                    maxFreq = maxFreq,
                    enabled = online,
                )
            )
        }

        val coreLoad = cpuLoad[-1]?.toFloat() ?: 0f
        val soc = CpuCodenameUtils.getSocByCpuMode()
        val cpuInfoModel = CpuInfoModel(
            coreCluster = clusterInfo.joinToString(separator = "+") {
                it.size.toString()
            },
            coreLoad = coreLoad.div(100f),
            coreLoadText = "%d%%".format(coreLoad.toInt()),
            coreTemperature = 25f,
            coreTemperatureText = "%.1f°C".format(25f),
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
