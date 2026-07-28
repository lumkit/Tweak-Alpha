package io.github.lumkit.tweak.common.utils

/**
 * 从 assets 安装 busybox / 自定义 toybox（对齐 Scene/vtools）。
 * 进程列表优先使用自定义 toybox，避免系统 toybox 对 CJK 进程名的列宽问题。
 */
expect object ToolkitInstaller {

    /**
     * 安装自定义 toybox，返回可执行文件绝对路径；失败返回空串。
     */
    suspend fun ensureToybox(): String

    /**
     * 若系统无 busybox，则安装私有 busybox 并把它的目录加入 Shell PATH。
     * @return 是否可用（系统已有或私有安装成功）
     */
    suspend fun ensureBusybox(): Boolean
}
