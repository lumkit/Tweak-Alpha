package io.github.lumkit.tweak.model

import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 界面外观。后台隐藏和缩放在 Activity 启动时就要读到，所以立即收集。
 * 模糊和底栏只在界面订阅时更新。
 */
object AppearanceSettingsStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val enabledBlur: StateFlow<Boolean> = TweakDataStore.enableBlurFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    val enabledLiquidGlass: StateFlow<Boolean> = TweakDataStore.floatNavBarEnableLiquidGlassFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    val enabledFloatNavBar: StateFlow<Boolean> = TweakDataStore.enableFloatNavigationBarFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    val hideInBackground: StateFlow<Boolean> = TweakDataStore.hideInBackgroundFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val globalScaleDensity: StateFlow<Float> = TweakDataStore.globalScaleDensityFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = 1.0f,
        )

    fun setHideInBackground(enabled: Boolean) {
        scope.launch {
            TweakDataStore.setHideInBackground(enabled)
        }
    }

    fun setGlobalScaleDensity(density: Float) {
        scope.launch {
            TweakDataStore.setGlobalScaleDensity(density)
        }
    }
}
