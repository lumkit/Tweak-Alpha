package io.github.lumkit.tweak.common.utils

import androidx.compose.runtime.Immutable
import io.github.lumkit.tweak.common.utils.AppsHelper.apps
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * 应用运行状态。
 *
 * 通过特权服务读取包管理器的启用设置解析得到，用于区分应用的可用性。
 */
enum class AppState {
    /** 已启用，正常可用 */
    ENABLED,

    /** 已禁用（`pm disable`，通常由系统或组件级别关闭） */
    DISABLED,

    /** 已冻结（`pm disable-user`，用户主动停用，图标从桌面隐藏） */
    FROZEN,
}

/**
 * 应用操作结果。
 *
 * 覆盖卸载、提取、禁用、冻结等特权操作的统一返回值。
 */
sealed interface AppOperationResult {
    data object Success : AppOperationResult

    data class Failure(val message: String) : AppOperationResult
}

/**
 * 应用信息数据类。
 *
 * 承载单个已安装应用的完整元信息，图标以 webp 形式缓存到应用私有目录，
 * 缓存路径通过 [iconPath] 暴露。
 */
@Immutable
@Serializable
data class AppInfo(
    /** 包名 */
    val packageName: String,
    /** 应用名称 */
    val appName: String,
    /** 版本名称 */
    val versionName: String,
    /** 版本号 */
    val versionCode: Long,
    /** 用户标识符（uid） */
    val uid: Int,
    /** 数据目录 */
    val dataDir: String,
    /** 安装目录（APK 路径） */
    val sourceDir: String,
    /** 最低 SDK 版本 */
    val minSdk: Int,
    /** 目标 SDK 版本 */
    val targetSdk: Int,
    /** 安装时间（毫秒时间戳） */
    val firstInstallTime: Long,
    /** 更新时间（毫秒时间戳） */
    val lastUpdateTime: Long,
    /** 图标缓存路径 */
    val iconPath: String,
    /** 是否为系统应用 */
    val isSystemApp: Boolean,
    /** 应用运行状态 */
    val state: AppState,
)

/**
 * 应用管理工具类。
 *
 * 负责通过特权服务采集设备上已安装的应用列表、缓存并自动更新图标，
 * 并提供卸载、提取、禁用、冻结、启动等特权操作。
 *
 * 设计要点：
 * - [apps] 以 [StateFlow] 暴露列表，UI 可直接响应式订阅。
 * - 通过监听应用安装/卸载/更新广播自动刷新列表。
 * - 卸载、禁用、冻结等操作完成后会自动刷新列表。
 * - 图标缓存到应用私有目录，应用更新后自动重新生成缓存。
 */
expect object AppsHelper {

    /** 已安装应用列表（响应式） */
    val apps: StateFlow<List<AppInfo>>

    /** 是否正在刷新列表 */
    val isRefreshing: StateFlow<Boolean>

    /** 初始化（注册广播、加载应用列表） */
    fun init()

    /** 根据包名获取图标缓存路径 */
    fun getIconPath(packageName: String): String

    /** 手动刷新应用列表 */
    suspend fun refresh()

    /**
     * 卸载应用。
     *
     * @param packageName 目标包名
     */
    suspend fun uninstall(packageName: String): AppOperationResult

    /**
     * 提取应用 APK 到指定目录（含 split APK）。
     *
     * @param packageName 目标包名
     * @param targetDir 目标目录绝对路径
     */
    suspend fun extractApk(packageName: String, targetDir: String): AppOperationResult

    /**
     * 设置应用禁用/启用状态。
     *
     * @param packageName 目标包名
     * @param disabled `true` 禁用，`false` 启用
     */
    suspend fun setDisabled(packageName: String, disabled: Boolean): AppOperationResult

    /**
     * 设置应用冻结/解冻状态。
     *
     * @param packageName 目标包名
     * @param frozen `true` 冻结（`pm disable-user`），`false` 解冻（`pm enable`）
     */
    suspend fun setFrozen(packageName: String, frozen: Boolean): AppOperationResult

    /**
     * 启动应用。
     *
     * @param packageName 目标包名
     */
    suspend fun launch(packageName: String): AppOperationResult
}
