package io.github.lumkit.tweak.common.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前台应用监控器
 *
 * 通过无障碍服务获取当前前台运行的应用包名。
 */
object ForegroundAppMonitor {

    internal val _foregroundPackage = MutableStateFlow<String?>(null)
    internal val _isRunning = MutableStateFlow(false)

    /** 当前前台应用包名（响应式） */
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    /** 无障碍服务是否正在运行 */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** 当前前台应用包名（非响应式） */
    val currentForegroundPackage: String?
        get() = _foregroundPackage.value
}
