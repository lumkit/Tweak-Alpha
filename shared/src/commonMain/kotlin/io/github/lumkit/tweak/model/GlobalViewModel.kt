package io.github.lumkit.tweak.model

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object GlobalViewModel: BaseViewModel() {

    /**
     * 用于手动初始化单例
     */
    fun create(){}

    /** 转发 [RuntimeModeStore.mode]，与文件层、守护进程读到的是同一个流。 */
    val runtimeModeState = RuntimeModeStore.mode

    val sfLatencySourceState = TweakDataStore.sfLatencySourceFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TweakDataStore.DEFAULT_SF_LATENCY_SOURCE,
        )

    /**
     * 解析当前运行模式。DataStore 在 Direct Boot / 文件锁异常时可能一直不发出值，
     * 调用方（Files、开机广播、Daemon）不能无限 [first]。
     */
    suspend fun currentRuntimeMode(timeout: Duration = 2.seconds): RuntimeMode {
        runtimeModeState.value?.let { return it }
        return withTimeoutOrNull(timeout) {
            runtimeModeState.filterNotNull().first()
        } ?: withTimeoutOrNull(timeout) {
            TweakDataStore.runtimeModeFlow().first()
        } ?: RuntimeMode.Unknow
    }

    val infoUpdateTimeSpanState = TweakDataStore.infoUpdateTimeSpanFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_LEVEL
        )

    val infoUpdateTimeSpanMillisecondsState = TweakDataStore.infoUpdateTimeSpanFlow()
        .distinctUntilChanged()
        .map {
            (it * TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_RANGE).toLong()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_LEVEL * TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_RANGE).toLong()
        )

    /** 电池记录采样间隔（ms），Eagerly：Daemon 采样循环可随时读 .value */
    val batteryRecordSampleIntervalMsState = TweakDataStore.batteryRecordSampleIntervalMsFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BatteryRecordDefaults.DEFAULT_INTERVAL_LEVEL *
                BatteryRecordDefaults.INTERVAL_LEVEL_RANGE_MS,
        )

    val processInfoUpdateTimeState = TweakDataStore.processInfoUpdateTimeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TweakDataStore.DEFAULT_PROCESS_INFO_UPDATE_TIME
        )

    val enabledBlur = TweakDataStore.enableBlurFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val enabledLiquidGlass = TweakDataStore.floatNavBarEnableLiquidGlassFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val enabledFloatNavBar = TweakDataStore.enableFloatNavigationBarFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val hideInBackgroundState = TweakDataStore.hideInBackgroundFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    // 全局缩放比
    val globalScaleDensityState = TweakDataStore.globalScaleDensityFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 1.0f
        )

    init {
        viewModelScope.launch {
            enabledFloatNavBar.collect {  }
        }
        viewModelScope.launch {
            hideInBackgroundState.collect { }
        }
        // 进程启动即加载电流校准，并持续与 DataStore 同步
        viewModelScope.launch {
            TweakDataStore.refreshBatteryCurrentCalibration()
            TweakDataStore.batteryDualCellFlow()
                .combine(TweakDataStore.batteryCurrentScaleFlow()) { dual, scale ->
                    dual to scale
                }
                .distinctUntilChanged()
                .collect { (dual, scale) ->
                    BatteryReadingNormalize.updateCalibration(
                        BatteryReadingNormalize.CurrentCalibration(
                            dualCell = dual,
                            scale = scale,
                        ),
                    )
                }
        }
    }

    fun setHideInBackground(enabled: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setHideInBackground(enabled)
        }
    }

    // 设置全局缩放比
    fun setGlobalScaleDensity(density: Float) {
        viewModelScope.launch {
            TweakDataStore.setGlobalScaleDensity(density)
        }
    }
}
