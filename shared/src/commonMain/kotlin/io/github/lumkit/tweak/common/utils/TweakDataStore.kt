package io.github.lumkit.tweak.common.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lumkit.tweak.common.daemon.BatteryRecordDaemonConfig
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
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
    // 全局缩放比
    private val globalScaleDensity = floatPreferencesKey("global_scale_density")

    // Shell相关
    private val shellTimeoutKey = longPreferencesKey("shell_timeout")
    private val runtimeModeKey = intPreferencesKey("runtime_mode")
    private val keepShellUserId = stringPreferencesKey("keep_shell_user_id")

    // Settings相关
    private val infoUpdateTimeSpan = intPreferencesKey("info_update_time_span")
    // 进程信息更新时间间隔
    private val processInfoUpdateTime = longPreferencesKey("process_info_update_time")
    // 电池记录采样间隔档位（×500ms，与面板刷新一致）
    private val batteryRecordSampleIntervalLevel = intPreferencesKey("battery_record_sample_interval_level")
    // 双电芯测量：规范化后电流 ×2
    private val batteryDualCell = booleanPreferencesKey("battery_dual_cell")
    // 电流数量级缩放（默认 -1000：µA→mA）
    private val batteryCurrentScale = longPreferencesKey("battery_current_scale")
    private val estimatedBatteryFullCapacityMah = intPreferencesKey("estimated_battery_full_capacity_mah")
    // Native Daemon（TweakServer）开关
    private val nativeDaemonEnabled = booleanPreferencesKey("native_daemon_enabled")
    // 自动启动应用开关
    private val autoStartAppSwitch = booleanPreferencesKey("auto_start_app_switch")
    // 后台隐藏：开启后不在最近任务列表显示
    private val hideInBackground = booleanPreferencesKey("hide_in_background")
    // 是否自动监听系统更新并推送通知（关闭后需进入系统更新页才发通知）
    private val autoListenSystemUpdate = booleanPreferencesKey("auto_listen_system_update")
    // 是否申请过通知权限
    private val hasRequestNotificationPermission = booleanPreferencesKey("has_request_notification_permission")
    // 是否已同意使用协议
    private val hasAcceptedUserAgreement = booleanPreferencesKey("has_accepted_user_agreement")
    private val infoPageEnabledProcessInfo = booleanPreferencesKey("info_page_enabled_process_info")
    private val infoPageItemOrderKey = stringPreferencesKey("info_page_item_order")
    private val infoPageEnabledCardsKey = stringPreferencesKey("info_page_enabled_cards")
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
    private val threadWatcherOverlayXKey = intPreferencesKey("thread_watcher_overlay_x")
    private val threadWatcherOverlayYKey = intPreferencesKey("thread_watcher_overlay_y")
    private val processThreadWatcherOverlayXKey = intPreferencesKey("process_thread_watcher_overlay_x")
    private val processThreadWatcherOverlayYKey = intPreferencesKey("process_thread_watcher_overlay_y")
    private val miniLoadWatcherOverlayXKey = intPreferencesKey("mini_load_watcher_overlay_x")
    private val miniLoadWatcherOverlayYKey = intPreferencesKey("mini_load_watcher_overlay_y")


    private val fpsSourcePathKey = stringPreferencesKey("fps_source_path")
    private val fpsSourceTokenIndexKey = intPreferencesKey("fps_source_token_index")
    private val fpsSourceDividerKey = floatPreferencesKey("fps_source_divider")
    private val sfLatencySourceKey = intPreferencesKey("sf_latency_source")

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

    fun globalScaleDensityFlow(): Flow<Float> = preferences.data.map {
        it[globalScaleDensity] ?: 1.0f
    }

    suspend fun setGlobalScaleDensity(density: Float) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[globalScaleDensity] = density
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
    /** SurfaceFlinger latency 来源：1=第二列，2=第三列（默认） */
    const val DEFAULT_SF_LATENCY_SOURCE = 2

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

    /**
     * 电池记录采样间隔档位，默认 2 → 1000ms（档位 × [io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults.INTERVAL_LEVEL_RANGE_MS]）。
     */
    fun batteryRecordSampleIntervalLevelFlow(): Flow<Int> = preferences.data.map {
        it[batteryRecordSampleIntervalLevel] ?: BatteryRecordDefaults.DEFAULT_INTERVAL_LEVEL
    }

    fun batteryRecordSampleIntervalMsFlow(): Flow<Int> = batteryRecordSampleIntervalLevelFlow().map { level ->
        level * BatteryRecordDefaults.INTERVAL_LEVEL_RANGE_MS
    }

    suspend fun setBatteryRecordSampleIntervalLevel(level: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[batteryRecordSampleIntervalLevel] = level
            }
        }
        BatteryRecordDaemonConfig.syncFromDataStore()
    }

    fun batteryDualCellFlow(): Flow<Boolean> = preferences.data.map {
        it[batteryDualCell] ?: false
    }

    suspend fun setBatteryDualCell(enabled: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[batteryDualCell] = enabled
            }
        }
        refreshBatteryCurrentCalibration()
        BatteryRecordDaemonConfig.syncFromDataStore()
    }

    fun batteryCurrentScaleFlow(): Flow<Long> = preferences.data.map {
        BatteryReadingNormalize.coerceScale(
            it[batteryCurrentScale] ?: BatteryReadingNormalize.DEFAULT_SCALE,
        )
    }

    suspend fun setBatteryCurrentScale(scale: Long) {
        val coerced = BatteryReadingNormalize.coerceScale(scale)
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[batteryCurrentScale] = coerced
            }
        }
        refreshBatteryCurrentCalibration()
        BatteryRecordDaemonConfig.syncFromDataStore()
    }

    fun estimatedBatteryFullCapacityMahFlow(): Flow<Int?> = preferences.data.map {
        it[estimatedBatteryFullCapacityMah]
    }

    suspend fun setEstimatedBatteryFullCapacityMah(value: Int?) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                if (value == null || value <= 0) {
                    preferences.remove(estimatedBatteryFullCapacityMah)
                } else {
                    preferences[estimatedBatteryFullCapacityMah] = value
                }
            }
        }
    }

    /** 从 DataStore 当前值刷新进程内电流校准缓存。 */
    suspend fun refreshBatteryCurrentCalibration() {
        val dual = batteryDualCellFlow().firstOrNull() ?: false
        val scale = batteryCurrentScaleFlow().firstOrNull()
            ?: BatteryReadingNormalize.DEFAULT_SCALE
        BatteryReadingNormalize.updateCalibration(
            BatteryReadingNormalize.CurrentCalibration(
                dualCell = dual,
                scale = scale,
            ),
        )
    }

    fun nativeDaemonEnabledFlow(): Flow<Boolean> = preferences.data.map {
        it[nativeDaemonEnabled] ?: false
    }

    suspend fun setNativeDaemonEnabled(enable: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[nativeDaemonEnabled] = enable
            }
        }
        BatteryRecordDaemonConfig.syncFromDataStore()
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

    fun hideInBackgroundFlow(): Flow<Boolean> = preferences.data.map {
        it[hideInBackground] ?: false
    }

    suspend fun setHideInBackground(enable: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[hideInBackground] = enable
            }
        }
    }

    fun autoListenSystemUpdateFlow(): Flow<Boolean> = preferences.data.map {
        it[autoListenSystemUpdate] ?: false
    }

    suspend fun setAutoListenSystemUpdate(enable: Boolean) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[autoListenSystemUpdate] = enable
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

    fun threadWatcherOverlayPositionFlow(): Flow<Pair<Int, Int>?> = preferences.data.map {
        val x = it[threadWatcherOverlayXKey]
        val y = it[threadWatcherOverlayYKey]
        if (x != null && y != null) x to y else null
    }

    val threadWatcherOverlayPosition: Pair<Int, Int>?
        get() = runBlocking {
            threadWatcherOverlayPositionFlow().firstOrNull()
        }

    suspend fun setThreadWatcherOverlayPosition(x: Int, y: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[threadWatcherOverlayXKey] = x
                preferences[threadWatcherOverlayYKey] = y
            }
        }
    }

    fun processThreadWatcherOverlayPositionFlow(): Flow<Pair<Int, Int>?> = preferences.data.map {
        val x = it[processThreadWatcherOverlayXKey]
        val y = it[processThreadWatcherOverlayYKey]
        if (x != null && y != null) x to y else null
    }

    val processThreadWatcherOverlayPosition: Pair<Int, Int>?
        get() = runBlocking {
            processThreadWatcherOverlayPositionFlow().firstOrNull()
        }

    suspend fun setProcessThreadWatcherOverlayPosition(x: Int, y: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[processThreadWatcherOverlayXKey] = x
                preferences[processThreadWatcherOverlayYKey] = y
            }
        }
    }

    fun miniLoadWatcherOverlayPositionFlow(): Flow<Pair<Int, Int>?> = preferences.data.map {
        val x = it[miniLoadWatcherOverlayXKey]
        val y = it[miniLoadWatcherOverlayYKey]
        if (x != null && y != null) x to y else null
    }

    val miniLoadWatcherOverlayPosition: Pair<Int, Int>?
        get() = runBlocking {
            miniLoadWatcherOverlayPositionFlow().firstOrNull()
        }

    suspend fun setMiniLoadWatcherOverlayPosition(x: Int, y: Int) {
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[miniLoadWatcherOverlayXKey] = x
                preferences[miniLoadWatcherOverlayYKey] = y
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

    fun sfLatencySourceFlow(): Flow<Int> = preferences.data.map {
        when (it[sfLatencySourceKey]) {
            1 -> 1
            else -> DEFAULT_SF_LATENCY_SOURCE
        }
    }

    suspend fun setSfLatencySource(source: Int) {
        val normalized = if (source == 1) 1 else DEFAULT_SF_LATENCY_SOURCE
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[sfLatencySourceKey] = normalized
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

    fun infoPageItemOrderFlow(): Flow<List<String>> = preferences.data.map {
        normalizeInfoPageItemOrder(it[infoPageItemOrderKey])
    }

    suspend fun setInfoPageItemOrder(order: List<String>) {
        val normalized = normalizeInfoPageItemOrder(order.joinToString(","))
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[infoPageItemOrderKey] = normalized.joinToString(",")
            }
        }
    }

    fun infoPageEnabledCardsFlow(): Flow<Set<String>> = preferences.data.map {
        normalizeInfoPageEnabledCards(it[infoPageEnabledCardsKey])
    }

    suspend fun setInfoPageEnabledCards(enabled: Set<String>) {
        val known = DEFAULT_INFO_PAGE_CARD_KEYS.toSet()
        var normalized = DEFAULT_INFO_PAGE_CARD_KEYS.filter { it in enabled && it in known }
        if (normalized.isEmpty()) {
            normalized = DEFAULT_INFO_PAGE_CARD_KEYS
        }
        preferences.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[infoPageEnabledCardsKey] = normalized.joinToString(",")
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

/**
 * 信息页卡片顺序：保留已知 key 的相对顺序，并补齐缺失项。
 */
val DEFAULT_INFO_PAGE_ITEM_ORDER = listOf("cpu", "memory", "gpu", "more")

/** 信息页可单独开关的卡片（电池/存储同属 more 分区）。 */
val DEFAULT_INFO_PAGE_CARD_KEYS = listOf("cpu", "memory", "gpu", "battery", "storage")

fun normalizeInfoPageEnabledCards(raw: String?): Set<String> {
    val known = DEFAULT_INFO_PAGE_CARD_KEYS.toSet()
    if (raw == null) {
        return known
    }
    return raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it in known }
        .toSet()
        .ifEmpty { known }
}

fun isInfoPageSectionVisible(sectionKey: String, enabledCards: Set<String>): Boolean {
    return when (sectionKey) {
        "more" -> "battery" in enabledCards || "storage" in enabledCards
        else -> sectionKey in enabledCards
    }
}

fun normalizeInfoPageItemOrder(raw: String?): List<String> {
    val defaults = DEFAULT_INFO_PAGE_ITEM_ORDER
    val known = defaults.toSet()
    val parsed = raw
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && it in known }
        ?.distinct()
        .orEmpty()
        .toMutableList()
    defaults.forEach { key ->
        if (key !in parsed) {
            parsed.add(key)
        }
    }
    return parsed
}

data class FpsSourceConfig(
    val path: String,
    val tokenIndex: Int,
    val divider: Float,
)
