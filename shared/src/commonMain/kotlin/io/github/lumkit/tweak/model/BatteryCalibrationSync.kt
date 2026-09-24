package io.github.lumkit.tweak.model

import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 进程启动后把 DataStore 里的双电芯和电流倍率写进读数缓存，并持续跟上后续修改。
 */
object BatteryCalibrationSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
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
}
