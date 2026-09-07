package io.github.lumkit.tweak.model

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object GlobalViewModel: BaseViewModel() {

    /**
     * 用于手动初始化单例
     */
    fun create(){}

    /**
     * Eagerly：Daemon / Files / Shell 等路径会 [filterNotNull].first()，
     * WhileSubscribed 在无收集者时可能长期停在 null，导致 Splash/Daemon 概率性永久挂起。
     */
    val runtimeModeState = TweakDataStore.runtimeModeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

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
