package io.github.lumkit.tweak.common.database.battery

import java.io.Closeable

/**
 * 监听 `battery_logs` 目录事件。
 * [onCreated]/[onAppended]、[onRemoved] 收到的是绝对路径。
 */
expect object BatteryRecordLogWatcher {
    fun start(
        onCreated: suspend (path: String) -> Unit,
        onAppended: suspend (path: String) -> Unit,
        onRemoved: suspend (path: String) -> Unit,
    ): Closeable
}
