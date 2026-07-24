package io.github.lumkit.tweak.ui.screen.settings

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import kotlin.math.roundToInt

class SettingsViewModel : BaseViewModel() {

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _notificationPermission = MutableStateFlow(false)
    val notificationPermission = _notificationPermission.asStateFlow()

    val themeMode = TweakDataStore.themeModeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ColorSchemeMode.System,
        )

    val runtimeMode = TweakDataStore.runtimeModeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RuntimeMode.Unknow,
        )

    val infoUpdateTimeSpanLevel = TweakDataStore.infoUpdateTimeSpanFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_LEVEL,
        )

    val autoStartApp = TweakDataStore.autoStartAppSwitchFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val autoListenSystemUpdate = TweakDataStore.autoListenSystemUpdateFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val hasRequestNotificationPermission = TweakDataStore.hasRequestNotificationPermission()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val apkExportDir = TweakDataStore.apkExportDirFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TweakDataStore.DEFAULT_APK_EXPORT_DIR,
        )

    val enableFloatNavigationBar = TweakDataStore.enableFloatNavigationBarFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    val enableBlur = TweakDataStore.enableBlurFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    val floatNavigationBarEnableLiquidity = TweakDataStore.floatNavBarEnableLiquidGlassFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    val enableProcessInfoOverview = TweakDataStore.infoPageEnabledProcessInfoFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    val batteryRecordSampleIntervalLevel = TweakDataStore.batteryRecordSampleIntervalLevelFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BatteryRecordDefaults.DEFAULT_INTERVAL_LEVEL,
        )

    fun updateIsIgnoringBatteryOptimizations(isIgnoring: Boolean) {
        _isIgnoringBatteryOptimizations.value = isIgnoring
    }

    fun updateNotificationPermission(permission: Boolean) {
        _notificationPermission.value = permission
    }

    fun setThemeMode(mode: ColorSchemeMode) {
        viewModelScope.launch {
            TweakDataStore.setThemeMode(mode)
        }
    }

    fun setRuntimeMode(mode: RuntimeMode, after: (() -> Unit)? = null) {
        viewModelScope.launch {
            TweakDataStore.setRuntimeMode(mode)
            after?.invoke()
        }
    }

    fun setInfoUpdateTimeSpan(level: Float) {
        viewModelScope.launch {
            TweakDataStore.setInfoUpdateTimeSpan(level.roundToInt())
        }
    }

    fun setBatteryRecordSampleIntervalLevel(level: Float) {
        viewModelScope.launch {
            TweakDataStore.setBatteryRecordSampleIntervalLevel(level.roundToInt())
        }
    }

    fun setAutoStartApp(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setAutoStartAppSwitch(enable)
        }
    }

    fun setAutoListenSystemUpdate(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setAutoListenSystemUpdate(enable)
        }
    }

    fun setHasRequestNotificationPermission() {
        viewModelScope.launch {
            TweakDataStore.setHasRequestNotificationPermission()
        }
    }

    fun setApkExportDir(path: String) {
        viewModelScope.launch {
            TweakDataStore.setApkExportDir(path)
        }
    }

    fun setEnableFloatNavigationBar(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setFloatNavigationBarEnabled(enable)
        }
    }

    fun setEnableBlur(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setBlurEnabled(enable)
        }
    }

    fun setFloatNavigationBarEnableLiquidity(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setFloatNavBarEnableLiquidGlassEnabled(enable)
        }
    }

    fun setEnableProcessInfoOverview(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setInfoPageEnabledProcessInfo(enable)
        }
    }
}
