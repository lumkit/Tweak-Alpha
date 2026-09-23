package io.github.lumkit.tweak.common.crash

import android.os.Process
import android.system.Os
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.getOrNull
import java.io.File

actual object CrashLogStore {

    private const val DIR = "${ConstCommon.Path.TWEAK_ALPHA_ROOT}/crashes"
    private const val LOCAL_DIR_NAME = "crashes"

    actual fun write(source: CrashLogSource, threadName: String, throwable: Throwable): String? {
        val timestamp = System.currentTimeMillis()
        val name = "${source.fileToken}_${timestamp}_${Process.myPid()}.log"
        val text = render(source, timestamp, threadName, throwable)
        if (Process.myUid() == 0) {
            return writeWorkFile(name, text)
        }
        // 应用进程无法在 /data/local/tmp 下建目录（属主是 shell，SELinux 也会拒绝）。
        // 先落到私有目录，特权就绪后再发布到工作区。
        return runCatching {
            val dir = localDir()
            if (!dir.isDirectory && !dir.mkdirs()) return@runCatching null
            val file = File(dir, name)
            file.writeText(text)
            file.absolutePath
        }.getOrNull()
    }

    actual suspend fun publishPending() {
        val pending = runCatching {
            localDir().listFiles()?.filter { it.isFile && it.name.endsWith(".log") }.orEmpty()
        }.getOrDefault(emptyList())
        if (pending.isEmpty()) return
        ReusableShells.execSync(
            "mkdir -p ${DIR.shellQuote()} && chmod 777 ${DIR.shellQuote()}",
        )
        for (file in pending) {
            val dest = "$DIR/${file.name}"
            val written = runCatching { Files.writeText(dest, file.readText()) }.getOrNull()
            if (written is NativeFileResult.Success) {
                runCatching { Files.chmod(dest, "0644") }
                file.delete()
            }
        }
    }

    actual suspend fun list(): List<CrashLogEntry> {
        runCatching { publishPending() }
        val fromWork = Files.list(DIR).getOrNull().orEmpty().mapNotNull { raw ->
            val path = if (raw.startsWith("/")) raw else "$DIR/${raw.substringAfterLast('/')}"
            readEntry(path, Files.readText(path).getOrNull().orEmpty())
        }
        val fromLocal = runCatching {
            localDir().listFiles()?.mapNotNull { file ->
                readEntry(file.absolutePath, file.readText())
            }.orEmpty()
        }.getOrDefault(emptyList())
        return (fromWork + fromLocal)
            .distinctBy { it.path.substringAfterLast('/') }
            .sortedByDescending { it.timestamp }
    }

    actual suspend fun read(path: String): String {
        val localRoot = runCatching { localDir().absolutePath }.getOrNull()
        if (localRoot != null && path.startsWith("$localRoot/")) {
            return runCatching { File(path).readText() }.getOrDefault("")
        }
        if (!path.startsWith("$DIR/")) return ""
        return Files.readText(path).getOrNull().orEmpty()
    }

    private fun writeWorkFile(name: String, text: String): String? {
        return runCatching {
            val dir = File(DIR)
            if (!dir.isDirectory && !dir.mkdirs()) return@runCatching null
            runCatching { Os.chmod(dir.absolutePath, 511) }
            val file = File(dir, name)
            file.writeText(text)
            runCatching { Os.chmod(file.absolutePath, 420) }
            file.absolutePath
        }.getOrNull()
    }

    private fun localDir(): File = File(application.filesDir, LOCAL_DIR_NAME)

    private fun readEntry(path: String, head: String): CrashLogEntry? {
        val name = path.substringAfterLast('/')
        if (!name.endsWith(".log")) return null
        val parsed = parseFileName(name) ?: return null
        val exception = head.lineSequence()
            .firstOrNull { it.startsWith("Exception: ") }
            ?.removePrefix("Exception: ")
            ?.trim()
            .orEmpty()
            .ifBlank { name }
        return CrashLogEntry(
            path = path,
            source = parsed.first,
            timestamp = parsed.second,
            exceptionName = exception,
            preview = previewBody(head),
        )
    }

    private fun parseFileName(name: String): Pair<CrashLogSource, Long>? {
        val match = FILE_NAME.matchEntire(name) ?: return null
        val source = when (match.groupValues[1]) {
            CrashLogSource.DAEMON.fileToken -> CrashLogSource.DAEMON
            CrashLogSource.CLIENT.fileToken -> CrashLogSource.CLIENT
            else -> return null
        }
        val timestamp = match.groupValues[2].toLongOrNull() ?: return null
        return source to timestamp
    }

    private fun previewBody(text: String): String {
        val lines = text.lineSequence().toList()
        val bodyStart = lines.indexOfFirst { it.isBlank() }.let { index ->
            if (index < 0) 0 else index + 1
        }
        val body = lines.drop(bodyStart).filter { it.isNotBlank() }
        val picked = if (body.isNotEmpty()) {
            body
        } else {
            lines.filter { it.startsWith("Exception: ") || it.startsWith("Message: ") }
        }
        return picked.take(PREVIEW_LINES).joinToString("\n")
    }

    private fun render(
        source: CrashLogSource,
        timestamp: Long,
        threadName: String,
        throwable: Throwable,
    ): String {
        val root = generateSequence(throwable) { it.cause }.last()
        return buildString {
            append("Source: ").append(source.fileToken).append('\n')
            append("Time: ").append(timestamp).append('\n')
            append("Pid: ").append(Process.myPid()).append('\n')
            append("Uid: ").append(Process.myUid()).append('\n')
            append("Thread: ").append(threadName).append('\n')
            append("Exception: ").append(root::class.qualifiedName ?: root.javaClass.name).append('\n')
            append("Message: ").append(root.message.orEmpty()).append('\n')
            append('\n')
            append(throwable.stackTraceToString())
        }
    }

    private val FILE_NAME = Regex("(daemon|client)_(\\d+)_\\d+\\.log")
    private const val PREVIEW_LINES = 5
}

private val CrashLogSource.fileToken: String
    get() = when (this) {
        CrashLogSource.DAEMON -> "daemon"
        CrashLogSource.CLIENT -> "client"
    }

private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
