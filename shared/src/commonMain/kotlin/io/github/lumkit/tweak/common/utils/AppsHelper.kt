package io.github.lumkit.tweak.common.utils

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * 应用信息数据类
 */
@Immutable
@Serializable
data class AppInfo(
    /** 应用包名 */
    val packageName: String,
    /** 应用名称 */
    val appName: String,
    /** 应用版本名 */
    val versionName: String,
    /** 应用版本号 */
    val versionCode: Long,
    /** 图标缓存路径 */
    val iconPath: String,
    /** 是否为系统应用 */
    val isSystemApp: Boolean,
)

/**
 * 应用列表工具类
 *
 * 负责获取设备上已安装的应用列表，缓存应用图标，
 * 并通过广播监听应用安装/卸载事件自动更新列表。
 */
expect object AppsHelper {

    /** 已安装应用列表（响应式） */
    val apps: StateFlow<List<AppInfo>>

    /** 初始化（注册广播、加载应用列表） */
    fun init()

    /** 根据包名获取图标缓存路径 */
    fun getIconPath(packageName: String): String

    /** 手动刷新应用列表 */
    suspend fun refresh()
}
