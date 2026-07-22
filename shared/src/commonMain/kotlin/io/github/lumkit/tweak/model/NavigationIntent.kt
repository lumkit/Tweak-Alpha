package io.github.lumkit.tweak.model

import io.github.lumkit.tweak.navigation.Screen
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 旧版目标枚举，仅兼容历史 PendingIntent / 调用方。
 * 实际跳转以 [NavigationIntent.screenJson] 多态反序列化为 [Screen] 为准。
 */
@Serializable
enum class NavigationIntentTargetScreen {
    UpdateSystem,
    FpsRecord,
    FpsRecordDetail,
    FlashRom,
    AppManager,
    ProcessManager,
    OpenSources,
}

@Serializable
data class NavigationIntent(
    val targetScreen: NavigationIntentTargetScreen? = null,
    val screenJson: String,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }

        fun of(screen: Screen): NavigationIntent = NavigationIntent(
            targetScreen = screen.toTargetOrNull(),
            screenJson = json.encodeToString(screen),
        )

        fun encode(screen: Screen): String = json.encodeToString(of(screen))

        fun decodeScreen(screenJson: String): Screen? =
            runCatching { json.decodeFromString<Screen>(screenJson) }.getOrNull()
    }
}

/**
 * 是否允许通过 Deeplink / Intent 打开。
 * Splash / Main / FilePicker 等内部页不开放。
 */
fun Screen.isDeeplinkNavigable(): Boolean = when (this) {
    is Screen.Splash,
    is Screen.Main,
    is Screen.Crash,
    is Screen.FilePicker,
    -> false
    else -> true
}

fun Screen.toTargetOrNull(): NavigationIntentTargetScreen? = when (this) {
    is Screen.UpdateSystem -> NavigationIntentTargetScreen.UpdateSystem
    is Screen.FpsRecord -> NavigationIntentTargetScreen.FpsRecord
    is Screen.FpsRecordDetail -> NavigationIntentTargetScreen.FpsRecordDetail
    is Screen.FlashRom -> NavigationIntentTargetScreen.FlashRom
    is Screen.AppManager -> NavigationIntentTargetScreen.AppManager
    is Screen.ProcessManager -> NavigationIntentTargetScreen.ProcessManager
    is Screen.OpenSources -> NavigationIntentTargetScreen.OpenSources
    else -> null
}

/**
 * 解析 adb / 简易 Intent 的 `route` 与可选参数。
 *
 * 支持的 route 名（大小写不敏感，支持下划线）：
 * - ProcessManager（可选 scrollToPackage / scrollToPid）
 * - AppManager / UpdateSystem / FpsRecord / FlashRom / OpenSources
 * - FpsRecordDetail（需要 id）
 */
fun parseDeeplinkRoute(
    route: String,
    stringExtra: (String) -> String?,
    intExtra: (String) -> Int?,
    longExtra: (String) -> Long?,
): Screen? {
    val key = route.trim()
        .replace('-', '_')
        .lowercase()
        .replace("_", "")
    return when (key) {
        "processmanager" -> Screen.ProcessManager(
            scrollToPackage = stringExtra("scrollToPackage").orEmpty(),
            scrollToPid = intExtra("scrollToPid") ?: -1,
        )
        "appmanager" -> Screen.AppManager
        "updatesystem" -> Screen.UpdateSystem
        "fpsrecord" -> Screen.FpsRecord
        "fpsrecorddetail" -> {
            val id = longExtra("id") ?: intExtra("id")?.toLong() ?: return null
            Screen.FpsRecordDetail(id = id)
        }
        "flashrom" -> Screen.FlashRom
        "opensources" -> Screen.OpenSources
        else -> null
    }
}
