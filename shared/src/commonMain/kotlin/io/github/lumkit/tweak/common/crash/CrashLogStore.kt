package io.github.lumkit.tweak.common.crash

import io.github.lumkit.tweak.common.crash.CrashLogStore.publishPending


enum class CrashLogSource {
    DAEMON,
    CLIENT,
}

data class CrashLogEntry(
    val path: String,
    val source: CrashLogSource,
    val timestamp: Long,
    val exceptionName: String,
    /** 异常正文预览，最多五行。 */
    val preview: String,
)

/**
 * 客户端与 tweak_server 的崩溃文本。
 * 目录：`/data/local/tmp/tweak-alpha/crashes`，每次崩溃一个新文件。
 * 客户端先写入应用私有目录，[publishPending] 在特权就绪后拷到工作区。
 */
expect object CrashLogStore {
    fun write(source: CrashLogSource, threadName: String, throwable: Throwable): String?

    suspend fun publishPending()

    suspend fun list(): List<CrashLogEntry>

    suspend fun read(path: String): String
}
