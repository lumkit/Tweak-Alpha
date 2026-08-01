package io.github.lumkit.tweak.ui.screen.settings

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.daemon.TweakDaemon
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.AccessibilityBootstrap
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class SettingsViewModel : BaseViewModel() {

    companion object {
        const val NATIVE_DAEMON_TOGGLE_LOAD_ID = "nativeDaemonToggle"
        const val RUNTIME_MODE_TOGGLE_LOAD_ID = "runtimeModeToggle"
    }

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _notificationPermission = MutableStateFlow(false)
    val notificationPermission = _notificationPermission.asStateFlow()

    /** 触发立即探测 TweakServer 存活（开关切换 / 页面可见时） */
    private val nativeDaemonProbe =
        MutableSharedFlow<Unit>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        ).also { it.tryEmit(Unit) }

    /**
     * 开关 UI 绑定进程存活状态（非 DataStore 偏好）。
     * 页面有订阅时每 2s 探测；启停后立即刷新。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nativeDaemonRunning = nativeDaemonProbe
        .flatMapLatest {
            flow {
                while (true) {
                    emit(probeNativeDaemonRunning())
                    delay(2.seconds)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val a11yDaemonEnabled = TweakDataStore.a11yDaemonEnabledFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

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

    val batteryDualCell = TweakDataStore.batteryDualCellFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val batteryCurrentScale = TweakDataStore.batteryCurrentScaleFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BatteryReadingNormalize.DEFAULT_SCALE,
        )

    fun updateIsIgnoringBatteryOptimizations(isIgnoring: Boolean) {
        _isIgnoringBatteryOptimizations.value = isIgnoring
    }

    fun updateNotificationPermission(permission: Boolean) {
        _notificationPermission.value = permission
    }

    fun refreshNativeDaemonStatus() {
        nativeDaemonProbe.tryEmit(Unit)
    }

    fun setThemeMode(mode: ColorSchemeMode) {
        viewModelScope.launch {
            TweakDataStore.setThemeMode(mode)
        }
    }

    fun setRuntimeMode(mode: RuntimeMode, after: (() -> Unit)? = null) = suspendLaunch(
        id = RUNTIME_MODE_TOGGLE_LOAD_ID,
    ) {
        loading()
        // 切模式（尤其 Root→Shizuku）前先经 Binder 让旧守护进程自行退出；
        // Shizuku shell 通常杀不掉 Root 拉起的 tweak_server。
        runCatching { NativeDaemonController.stop() }
        TweakDataStore.setRuntimeMode(mode)
        // 保持 loading 到 after（可能 restartApp），避免切换瞬间弹窗闪灭
        after?.invoke()
        success()
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

    fun setBatteryDualCell(enabled: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setBatteryDualCell(enabled)
        }
    }

    fun setBatteryCurrentScaleIndex(index: Float) {
        viewModelScope.launch {
            val scale = BatteryReadingNormalize.scaleAtIndex(index.roundToInt())
            TweakDataStore.setBatteryCurrentScale(scale)
        }
    }

    fun setNativeDaemonEnabled(enable: Boolean) = suspendLaunch(
        id = NATIVE_DAEMON_TOGGLE_LOAD_ID,
    ) {
        loading()
        NativeDaemonController.setEnabled(enable)
        refreshNativeDaemonStatus()
        success()
    }

    fun setA11yDaemonEnabled(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setA11yDaemonEnabled(enable)
            if (enable) {
                AccessibilityBootstrap.enableIfPrivileged(requireUserPreference = false)
            } else {
                AccessibilityBootstrap.stopAccessibilityService()
            }
        }
    }

    fun setAutoStartApp(enable: Boolean) {
        viewModelScope.launch {
            TweakDataStore.setAutoStartAppSwitch(enable)
            if (!enable) {
                NativeDaemonController.stop()
            } else if (TweakDataStore.nativeDaemonEnabledFlow().first()) {
                NativeDaemonController.ensureRunning()
            }
            refreshNativeDaemonStatus()
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

    private suspend fun probeNativeDaemonRunning(): Boolean {
        return runCatching {
            TweakDaemon.isRunning() || TweakDaemon.ping()
        }.getOrDefault(false)
    }
}
