package io.github.lumkit.tweak.common.utils

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_device_type_autautomotive
import tweak_alpha.shared.generated.resources.text_device_type_emulator
import tweak_alpha.shared.generated.resources.text_device_type_phone
import tweak_alpha.shared.generated.resources.text_device_type_tablet
import tweak_alpha.shared.generated.resources.text_device_type_tv
import tweak_alpha.shared.generated.resources.text_device_type_unknown
import tweak_alpha.shared.generated.resources.text_device_type_watch

expect fun isDebugBuild(): Boolean

expect fun restartApp()

expect fun exitApp()

expect val SDK_INT: Int

expect val SDK_RELEASE: String

expect val BOARD: String

expect val BRAND: String

expect val MODEL: String

/**
* 获取应用是否忽略电池优化
 */
expect fun isIgnoringBatteryOptimizations(): Boolean

/**
 * 尝试设置应用忽略电池优化
 */
expect fun trySetIsIgnoringBatteryOptimizations()

/**
 * 获取应用是否有通知权限
 */
expect fun hasNotificationPermission(): Boolean

/**
 * 申请通知权限
 */
expect fun ManagedActivityResultLauncher<String, Boolean>.requestNotificationPermission()

/**
 * 通知权限是否被关闭
 */
expect fun areNotificationsEnabled(): Boolean

/**
 * 跳转至App详情
 */
expect fun jumpToAppInfo()

/**
 * 跳转至指定应用详情
 */
expect fun jumpToAppInfo(packageName: String)

@Immutable
enum class DeviceType {
    PHONE,
    TABLET,
    TV,
    WATCH,
    AUTOMOTIVE,
    EMULATOR,
    UNKNOWN
}

val DeviceType.displayNameResource: StringResource
    get() = when (this) {
        DeviceType.PHONE -> Res.string.text_device_type_phone
        DeviceType.TABLET -> Res.string.text_device_type_tablet
        DeviceType.TV -> Res.string.text_device_type_tv
        DeviceType.WATCH -> Res.string.text_device_type_watch
        DeviceType.AUTOMOTIVE -> Res.string.text_device_type_autautomotive
        DeviceType.EMULATOR -> Res.string.text_device_type_emulator
        DeviceType.UNKNOWN -> Res.string.text_device_type_unknown
    }

expect fun getDeviceType(): DeviceType

expect suspend fun getDeviceModel(): String

expect fun getDeviceScreenWidth(): Int

expect fun getDeviceScreenHeight(): Int

expect fun getDeviceScreenRefreshRate(): Float

expect val packageName: String

expect fun startKeepAliveService(isForegroundService: Boolean)

expect fun toastText(msg: String)

/**
 * 复制文本到系统剪贴板。
 *
 * @param text 要复制的内容
 * @param label 剪贴板条目标签（部分平台可见）
 */
expect fun copyTextToClipboard(text: String, label: String = "text")

expect val BUILD_VERSION_CODE: Long
expect val BUILD_VERSION_NAME: String

expect fun openUrl(url: String)
