package io.github.lumkit.tweak.model

import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 采样间隔和帧延迟来源。悬浮窗、信息页和电池记录直接读当前值，
 * 所以这里用 Eagerly，不依赖界面是否正在订阅。
 */
object SamplingSettingsStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sfLatencySource: StateFlow<Int> = TweakDataStore.sfLatencySourceFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = TweakDataStore.DEFAULT_SF_LATENCY_SOURCE,
        )

    val infoUpdateIntervalMs: StateFlow<Long> = TweakDataStore.infoUpdateTimeSpanFlow()
        .distinctUntilChanged()
        .map { level ->
            (level * TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_RANGE).toLong()
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = (
                TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_LEVEL *
                    TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_RANGE
                ).toLong(),
        )

    val batteryRecordIntervalMs: StateFlow<Int> = TweakDataStore.batteryRecordSampleIntervalMsFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = BatteryRecordDefaults.DEFAULT_INTERVAL_LEVEL *
                BatteryRecordDefaults.INTERVAL_LEVEL_RANGE_MS,
        )

    val processInfoUpdateMs: StateFlow<Long> = TweakDataStore.processInfoUpdateTimeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = TweakDataStore.DEFAULT_PROCESS_INFO_UPDATE_TIME,
        )
}
