package io.github.lumkit.tweak.common.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    // Shell相关
    private val shellTimeoutKey = longPreferencesKey("shell_timeout")
    private val runtimeModeKey = intPreferencesKey("runtime_mode")
    private val keepShellUserId = stringPreferencesKey("keep_shell_user_id")

    // Settings相关
    private val infoUpdateTimeSpan = intPreferencesKey("info_update_time_span")
    // 自动启动应用开关
    private val autoStartAppSwitch = booleanPreferencesKey("auto_start_app_switch")
    // 是否申请过通知权限
    private val hasRequestNotificationPermission = booleanPreferencesKey("has_request_notification_permission")

    /**
     * 电池信息显示类型，0 - 显示功率，1显示电流
     */
    private val infoBatteryDisplayType = intPreferencesKey("info_battery_display_type")

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
}

