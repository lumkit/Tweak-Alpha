package io.github.lumkit.tweak.common.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lumkit.tweak.model.BatteryDisplayType
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okio.Path
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

private const val DEFAULT_DATA_STORE_NAME = "tweak"
private const val DATA_STORE_FILE_SUFFIX = ".preferences_pb"

fun createPreferenceDataStore(name: String): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { createDataStorePath("$name$DATA_STORE_FILE_SUFFIX") }
    )
}

expect fun createDataStorePath(name: String): Path

val preferences: DataStore<Preferences> by lazy {
    createPreferenceDataStore(name = DEFAULT_DATA_STORE_NAME)
}

object TweakDataStore {

    // 主题相关
    private val themeModeKey = intPreferencesKey("theme_mode")
    private val backgroundImagePath = stringPreferencesKey("background_image_path")
    // 是否启用悬浮导航栏
    private val enableFloatNavigationBar = booleanPreferencesKey("enable_float_navigation_bar")
    // 是否启用全局模糊效果
    private val enableBlur = booleanPreferencesKey("enable_blur")
    // 悬浮导航栏是否启用液态玻璃效果
    private val floatNavBarEnableLiquidGlass = booleanPreferencesKey("float_nav_bar_enable_liquid_glass")

    // Shell相关
    private val shellTimeoutKey = longPreferencesKey("shell_timeout")
    private val runtimeModeKey = intPreferencesKey("runtime_mode")
    private val keepShellUserId = stringPreferencesKey("keep_shell_user_id")

    // Settings相关
    private val infoUpdateTimeSpan = intPreferencesKey("info_update_time_span")
    // 进程信息更新时间间隔
    private val processInfoUpdateTime = longPreferencesKey("process_info_update_time")
    // 自动启动应用开关
    private val autoStartAppSwitch = booleanPreferencesKey("auto_start_app_switch")
    // 是否申请过通知权限
    private val hasRequestNotificationPermission = booleanPreferencesKey("has_request_notification_permission")
    // 是否已同意使用协议
    private val hasAcceptedUserAgreement = booleanPreferencesKey("has_accepted_user_agreement")
    private val infoPageEnabledProcessInfo = booleanPreferencesKey("info_page_enabled_process_info")
    private val processManagerFilterModeKey = intPreferencesKey("process_manager_filter_mode")

    /**
     * 电池信息显示类型，0 - 显示功率，1显示电流
     */
    private val infoBatteryDisplayType = intPreferencesKey("info_battery_display_type")

    // FPS 悬浮窗位置
    private val fpsOverlayXKey = intPreferencesKey("fps_overlay_x")
    private val fpsOverlayYKey = intPreferencesKey("fps_overlay_y")
    private val loadWatcherOverlayXKey = intPreferencesKey("load_watcher_overlay_x")
    private val loadWatcherOverlayYKey = intPreferencesKey("load_watcher_overlay_y")
    private val fpsSourcePathKey = stringPreferencesKey("fps_source_path")
    private val fpsSourceTokenIndexKey = intPreferencesKey("fps_source_token_index")
    private val fpsSourceDividerKey = floatPreferencesKey("fps_source_divider")

    fun themeModeFlow(): Flow<ColorSchemeMode> = preferences.data.map {
        it[themeModeKey] ?: 0
    }.map {
        runCatching {
            ColorSchemeMode.entries[it]
        }.getOrNull() ?: ColorSchemeMode.System
    }

    suspend fun setThemeMode(themeMode: ColorSchemeMode) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[themeModeKey] = themeMode.ordinal
            }
        }
    }

    fun backgroundImagePathFlow(): Flow<String?> = preferences.data.map {
        it[backgroundImagePath]
    }

    suspend fun setBackgroundImagePath(path: String) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[backgroundImagePath] = path
            }
        }
    }

    fun enableFloatNavigationBarFlow(): Flow<Boolean> = preferences.data.map {
        it[enableFloatNavigationBar] ?: true
    }

    fun enableBlurFlow(): Flow<Boolean> = preferences.data.map {
        it[enableBlur] ?: true
    }

    fun floatNavBarEnableLiquidGlassFlow(): Flow<Boolean> = preferences.data.map {
        it[floatNavBarEnableLiquidGlass] ?: true
    }

    suspend fun setFloatNavigationBarEnabled(enabled: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[enableFloatNavigationBar] = enabled
            }
        }
    }

    suspend fun setBlurEnabled(enabled: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[enableBlur] = enabled
            }
        }
    }

    suspend fun setFloatNavBarEnableLiquidGlassEnabled(enabled: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[floatNavBarEnableLiquidGlass] = enabled
            }
        }
    }

    private const val DEFAULT_S_TIMEOUT_MILLISECONDS = 15_000L

    fun shellTimeoutFlow(): Flow<Long> = preferences.data.map {
        it[shellTimeoutKey] ?: DEFAULT_S_TIMEOUT_MILLISECONDS
    }

    suspend fun setShellTimeout(timeout: Long) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[shellTimeoutKey] = timeout
            }
        }
    }

    val shellTimeoutMilliseconds: Long
        get() = runBlocking {
            shellTimeoutFlow().firstOrNull() ?: DEFAULT_S_TIMEOUT_MILLISECONDS
        }

    fun runtimeModeFlow(): Flow<RuntimeMode> = preferences.data.map {
        it[runtimeModeKey] ?: 0
    }.map {
        runCatching {
            RuntimeMode.entries[it]
        }.getOrNull() ?: RuntimeMode.Unknow
    }

    suspend fun setRuntimeMode(runtimeMode: RuntimeMode) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[runtimeModeKey] = runtimeMode.ordinal
            }
        }
    }

    const val DEFAULT_INFO_UPDATE_TIME_SP_LEVEL = 2
    const val DEFAULT_INFO_UPDATE_TIME_SP_RANGE = 500f
    const val DEFAULT_PROCESS_INFO_UPDATE_TIME = 3000L

    fun infoUpdateTimeSpanFlow(): Flow<Int> = preferences.data.map {
        it[infoUpdateTimeSpan] ?: DEFAULT_INFO_UPDATE_TIME_SP_LEVEL
    }

    suspend fun setInfoUpdateTimeSpan(span: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[infoUpdateTimeSpan] = span
            }
        }
    }

    // 进程信息更新时间间隔
    fun processInfoUpdateTimeFlow(): Flow<Long> = preferences.data.map {
        it[processInfoUpdateTime] ?: DEFAULT_PROCESS_INFO_UPDATE_TIME
    }

    suspend fun setProcessInfoUpdateTime(span: Long) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[processInfoUpdateTime] = span
            }
        }
    }

    fun infoBatteryDisplayTypeFlow(): Flow<BatteryDisplayType> = preferences.data.map {
        it[infoBatteryDisplayType] ?: 0
    }.map {
        runCatching {
            BatteryDisplayType.entries[it]
        }.getOrNull() ?: BatteryDisplayType.Power
    }

    suspend fun setInfoBatteryDisplayType(type: BatteryDisplayType) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[infoBatteryDisplayType] = type.ordinal
            }
        }
    }

    fun autoStartAppSwitchFlow(): Flow<Boolean> = preferences.data.map {
        it[autoStartAppSwitch] ?: false
    }

    suspend fun setAutoStartAppSwitch(enable: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[autoStartAppSwitch] = enable
            }
        }
    }

    fun keepShellUserIdFlow(): Flow<String?> = preferences.data.map {
        it[keepShellUserId]
    }

    suspend fun setKeepShellUserId(userId: String) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[keepShellUserId] = userId
            }
        }
    }

    fun hasRequestNotificationPermission(): Flow<Boolean> = preferences.data.map {
        it[hasRequestNotificationPermission] ?: false
    }

    suspend fun setHasRequestNotificationPermission() {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[hasRequestNotificationPermission] = true

            }
        }
    }

    fun hasAcceptedUserAgreementFlow(): Flow<Boolean> = preferences.data.map {
        it[hasAcceptedUserAgreement] ?: false
    }

    suspend fun setHasAcceptedUserAgreement(accepted: Boolean = true) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[hasAcceptedUserAgreement] = accepted
            }
        }
    }

    fun fpsOverlayPositionFlow(): Flow<Pair<Int, Int>?> = preferences.data.map {
        val x = it[fpsOverlayXKey]
        val y = it[fpsOverlayYKey]
        if (x != null && y != null) x to y else null
    }

    val fpsOverlayPosition: Pair<Int, Int>?
        get() = runBlocking {
            fpsOverlayPositionFlow().firstOrNull()
        }

    suspend fun setFpsOverlayPosition(x: Int, y: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[fpsOverlayXKey] = x
                preferences[fpsOverlayYKey] = y
            }
        }
    }

    fun loadWatcherOverlayPositionFlow(): Flow<Pair<Int, Int>?> = preferences.data.map {
        val x = it[loadWatcherOverlayXKey]
        val y = it[loadWatcherOverlayYKey]
        if (x != null && y != null) x to y else null
    }

    val loadWatcherOverlayPosition: Pair<Int, Int>?
        get() = runBlocking {
            loadWatcherOverlayPositionFlow().firstOrNull()
        }

    suspend fun setLoadWatcherOverlayPosition(x: Int, y: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[loadWatcherOverlayXKey] = x
                preferences[loadWatcherOverlayYKey] = y
            }
        }
    }

    fun fpsSourceFlow(): Flow<FpsSourceConfig?> = preferences.data.map {
        val path = it[fpsSourcePathKey]?.takeIf(String::isNotBlank) ?: return@map null
        FpsSourceConfig(
            path = path,
            tokenIndex = it[fpsSourceTokenIndexKey] ?: 0,
            divider = it[fpsSourceDividerKey] ?: 1f,
        )
    }

    val fpsSource: FpsSourceConfig?
        get() = runBlocking {
            fpsSourceFlow().firstOrNull()
        }

    suspend fun setFpsSource(config: FpsSourceConfig) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[fpsSourcePathKey] = config.path
                preferences[fpsSourceTokenIndexKey] = config.tokenIndex
                preferences[fpsSourceDividerKey] = config.divider
            }
        }
    }

    suspend fun clearFpsSource() {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences.remove(fpsSourcePathKey)
                preferences.remove(fpsSourceTokenIndexKey)
                preferences.remove(fpsSourceDividerKey)
            }
        }
    }

    private val apkExportDirKey = stringPreferencesKey("apk_export_dir")

    /**
     * APK 提取目录；未设置时回落到公共 Download 目录。
     */
    fun apkExportDirFlow(): Flow<String> = preferences.data.map {
        it[apkExportDirKey]?.takeIf(String::isNotBlank) ?: DEFAULT_APK_EXPORT_DIR
    }

    suspend fun apkExportDir(): String {
        return apkExportDirFlow().firstOrNull() ?: DEFAULT_APK_EXPORT_DIR
    }

    suspend fun setApkExportDir(path: String) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[apkExportDirKey] = path.trim()
            }
        }
    }

    const val DEFAULT_APK_EXPORT_DIR = "/storage/emulated/0/Download"

    fun infoPageEnabledProcessInfoFlow(): Flow<Boolean> = preferences.data.map {
        it[infoPageEnabledProcessInfo] ?: true
    }

    suspend fun setInfoPageEnabledProcessInfo(enabled: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[infoPageEnabledProcessInfo] = enabled
            }
        }
    }

    fun processManagerFilterModeOrdinalFlow(): Flow<Int> = preferences.data.map {
        it[processManagerFilterModeKey] ?: 0
    }

    suspend fun setProcessManagerFilterModeOrdinal(ordinal: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[processManagerFilterModeKey] = ordinal
            }
        }
    }
}

data class FpsSourceConfig(
    val path: String,
    val tokenIndex: Int,
    val divider: Float,
)
