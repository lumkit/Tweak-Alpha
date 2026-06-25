package io.github.lumkit.tweak.common.shell

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * 输出监听器接口。
 */
fun interface OutputListener {
    fun onOutputReceived(line: String)
}

/**
 * 可复用 Shell 会话。
 */
expect class KeepShell() {
    val isIdle: Boolean

    fun tryExit()

    suspend fun doCmdSync(cmd: String): String

    fun doCmdWithListener(cmd: String, listener: OutputListener): Job

    fun doCmdAsFlow(cmd: String): Flow<String>

    fun stopMonitoring()

    fun doCmdSyncBlocking(cmd: String): String

    fun cleanup()
}
