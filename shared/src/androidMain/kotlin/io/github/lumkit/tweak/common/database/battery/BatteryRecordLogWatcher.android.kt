package io.github.lumkit.tweak.common.database.battery

import android.os.FileObserver
import io.github.lumkit.tweak.common.daemon.DaemonPaths
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

actual object BatteryRecordLogWatcher {

    private const val TAG = "BatteryRecordLogWatcher"
    private const val DEBOUNCE_MS = 200L
    private const val POLL_MS = 1_500L

    private val watchMask =
        FileObserver.CREATE or
            FileObserver.MODIFY or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.DELETE_SELF

    actual fun start(
        onCreated: suspend (path: String) -> Unit,
        onAppended: suspend (path: String) -> Unit,
        onRemoved: suspend (path: String) -> Unit,
    ): Closeable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val observerRef = AtomicReference<FileObserver?>(null)
        val pendingAppend = ConcurrentHashMap<String, Job>()
        val lengthCache = ConcurrentHashMap<String, Long>()
        val mtimeCache = ConcurrentHashMap<String, Long>()

        fun absolute(dir: String, name: String): String =
            if (name.startsWith("/")) name else "$dir/$name"

        fun isBatteryLog(name: String): Boolean =
            name.contains(".brlog") || name.endsWith(".log") || name.contains(".log.")

        fun debounceAppend(path: String) {
            pendingAppend.remove(path)?.cancel()
            pendingAppend[path] = scope.launch {
                delay(DEBOUNCE_MS)
                runCatching { onAppended(path) }
            }
        }

        fun attachObserver(dirPath: String) {
            observerRef.getAndSet(null)?.stopWatching()
            val dir = File(dirPath)
            dir.mkdirs()
            val canWatch = dir.isDirectory && dir.canRead() && runCatching { dir.list() != null }.getOrDefault(false)
            if (!canWatch) {
                logW("FileObserver probe failed for $dirPath, using poll fallback", TAG)
                return
            }
            val observer = object : FileObserver(dirPath, watchMask) {
                override fun onEvent(event: Int, path: String?) {
                    if ((event and DELETE_SELF) != 0 || (event and MOVE_SELF) != 0) {
                        scope.launch {
                            delay(300)
                            attachObserver(dirPath)
                        }
                        return
                    }
                    if (path.isNullOrBlank() || !isBatteryLog(path)) return
                    val abs = absolute(dirPath, path)
                    when {
                        (event and (CREATE or MOVED_TO)) != 0 -> {
                            scope.launch { runCatching { onCreated(abs) } }
                        }
                        (event and (DELETE or MOVED_FROM)) != 0 -> {
                            scope.launch { runCatching { onRemoved(abs) } }
                        }
                        (event and (MODIFY or CLOSE_WRITE)) != 0 -> {
                            // 含 header-only 更新（session 结束写 endedAt、长度不变）
                            debounceAppend(abs)
                        }
                    }
                }
            }
            observer.startWatching()
            observerRef.set(observer)
            logD("watching battery logs at $dirPath", TAG)
        }

        val pollJob = scope.launch {
            val paths = DaemonPaths.resolve()
            val dirPath = paths.batteryLogsDir
            File(dirPath).mkdirs()
            attachObserver(dirPath)

            while (isActive) {
                delay(POLL_MS)
                val dir = File(dirPath)
                if (!dir.isDirectory) {
                    dir.mkdirs()
                    attachObserver(dirPath)
                    continue
                }
                val files = dir.listFiles()?.filter { it.isFile && isBatteryLog(it.name) }.orEmpty()
                val seen = files.map { it.absolutePath }.toHashSet()
                for (f in files) {
                    val abs = f.absolutePath
                    val len = f.length()
                    val mtime = f.lastModified()
                    val prevLen = lengthCache.put(abs, len)
                    val prevMtime = mtimeCache.put(abs, mtime)
                    when {
                        prevLen == null -> runCatching { onCreated(abs) }
                        len > prevLen -> debounceAppend(abs)
                        // session 结束只改 header：长度不变但 mtime 变
                        prevMtime != null && mtime > prevMtime -> debounceAppend(abs)
                    }
                }
                val removed = lengthCache.keys.filter { it !in seen }
                for (path in removed) {
                    lengthCache.remove(path)
                    mtimeCache.remove(path)
                    runCatching { onRemoved(path) }
                }
            }
        }

        return Closeable {
            pollJob.cancel()
            pendingAppend.values.forEach { it.cancel() }
            pendingAppend.clear()
            observerRef.getAndSet(null)?.stopWatching()
        }
    }
}
