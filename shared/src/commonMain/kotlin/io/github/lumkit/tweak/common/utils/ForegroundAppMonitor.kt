package io.github.lumkit.tweak.common.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前台应用监控器。
 *
 * UID importance / 亮屏等事件触发后，用 dumpsys 焦点窗口解析当前前台包名。
 */
object ForegroundAppMonitor {

    internal val _foregroundPackage = MutableStateFlow<String?>(null)
    internal val _isRunning = MutableStateFlow(false)

    /** 当前前台应用包名（响应式） */
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    /** 监听是否已启动 */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** 当前前台应用包名（非响应式） */
    val currentForegroundPackage: String?
        get() = _foregroundPackage.value

    fun start() = startForegroundAppMonitor()

    suspend fun refresh() = refreshForegroundAppMonitor()
}

internal expect fun startForegroundAppMonitor()

internal expect suspend fun refreshForegroundAppMonitor()
