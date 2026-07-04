package io.github.lumkit.tweak.common.utils

import kotlinx.coroutines.flow.StateFlow

/**
 * 前台应用监控器
 *
 * 通过无障碍服务获取当前前台运行的应用包名。
 */
expect object ForegroundAppMonitor {

    /** 当前前台应用包名（响应式） */
    val foregroundPackage: StateFlow<String?>

    /** 无障碍服务是否正在运行 */
    val isRunning: StateFlow<Boolean>

    /** 当前前台应用包名（非响应式） */
    val currentForegroundPackage: String?
}
