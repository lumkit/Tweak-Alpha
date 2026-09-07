package io.github.lumkit.tweak.server.fps

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException

/**
 * 对齐 Frame.kt：Hidden API + dumpAsync，按墙钟间隔统计新帧算 FPS。
 * latency 来源 1 = 第二列 desired present，来源 2 = 第三列 actual present。
 * [shellExec] 仅用于 dumpAsync 失败时的 dumpsys 回退。
 */
@SuppressLint("PrivateApi")
class SurfaceFlingerFrameSampler(
    private val shellExec: ((String) -> String)? = null,
) {
    companion object {
        private const val SHELL_MARKER = "__SHELL_EOF_4f8c2a__"
        private const val MAX_IDLE_POLLS = 10

        private val focusInfoMethod by lazy {
            val atmInterface = Class.forName("android.app.IActivityTaskManager")
            runCatching { atmInterface.getMethod("getFocusedRootTaskInfo") }
                .getOrElse { atmInterface.getMethod("getFocusedStackInfo") }
        }
        private val atmService by lazy {
            Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService")
                .invoke(null)
        }
        private val topActivityField by lazy {
            focusInfoMethod.returnType.getField("topActivity")
        }
        private val getTasksMethod by lazy {
            Class.forName("android.app.IActivityManager")
                .getMethod("getTasks", Int::class.javaPrimitiveType)
        }
        private val amService by lazy {
            runCatching {
                Class.forName("android.app.ActivityManager")
                    .getMethod("getService")
                    .invoke(null)
            }.getOrElse {
                Class.forName("android.app.ActivityManagerNative")
                    .getMethod("getDefault")
                    .invoke(null)
            }
        }
        private val getServiceMethod by lazy {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
        }
        private val binderCache = HashMap<String, IBinder>()

        private val reSfModern = Regex("""RequestedLayerState\{(.+?)\s+parentId=""")
        private val noiseTokens = listOf(
            "Background for",
            "Bounds for",
            "ActivityRecord",
            "AtchDlg",
        )
        private val reHashSuffix = Regex("#(\\d+)")
        private val reAtSuffix = Regex("@(\\d+)")
        private val focusWindowPrefix = "mCurrentFocus=Window{"

        @Synchronized
        private fun getService(name: String): IBinder {
            return binderCache.getOrPut(name) {
                getServiceMethod.invoke(null, name) as? IBinder
                    ?: error("Could not get $name service")
            }
        }
    }

    private var shellProcess: Process? = null
    private var shellWriter: BufferedWriter? = null
    private var shellReader: BufferedReader? = null

    private var lastFocus = ""
    private var lastLayer = ""
    private var lastTimeNanos = 0L
    private var lastLatency = 0L
    private var lastFps = 0f
    private var lastLatencySource = 2
    private var idlePolls = 0
    private var binderDumpUsable = true

    private val tab: Byte = 9
    private val lf: Byte = 10
    private val digit0: Byte = 48
    private val digit9: Byte = 57
    private val ioBuffer = ByteArray(64 * 1024)

    @Synchronized
    fun currentFps(latencySource: Int = 2): Float {
        val source = if (latencySource == 1) 1 else 2
        if (source != lastLatencySource) {
            lastLatencySource = source
            lastLatency = 0L
            lastTimeNanos = 0L
            idlePolls = 0
            lastFps = 0f
        }
        val focus = resolveFocus()
        if (focus.isEmpty()) {
            lastFocus = ""
            lastLayer = ""
            lastFps = 0f
            return 0f
        }
        if (focus != lastFocus) {
            lastFocus = focus
            lastLayer = ""
            lastLatency = 0L
            lastTimeNanos = 0L
            idlePolls = 0
            lastFps = 0f
        }
        if (lastLayer.isNotEmpty()) {
            val fps = scanLatencyFps(lastLayer, source)
            if (fps != null) {
                lastFps = fps
                return fps
            }
            lastLayer = ""
        }
        val layer = currentLayer(focus).firstOrNull() ?: return 0f
        val fps = scanLatencyFps(layer, source)
        if (fps != null) {
            lastLayer = layer
            lastFps = fps
            return fps
        }
        return 0f
    }

    private fun resolveFocus(): String {
        binderFocus()?.let { return it }
        if (lastLayer.isNotEmpty()) return lastFocus
        return dumpsysFocus()
    }

    private fun dumpsysFocus(): String {
        val dump = runCatching {
            sh("""dumpsys window | grep -E "mCurrentFocus|mFocusedApp|mFocusedWindow"""")
        }.getOrDefault("")
        val line = dump.lineSequence().firstOrNull { it.contains("mCurrentFocus") } ?: dump
        if (!line.contains(focusWindowPrefix)) return ""
        return runCatching {
            line.substring(
                line.indexOf(focusWindowPrefix) + focusWindowPrefix.length,
                line.lastIndexOf('}'),
            ).trim().split(Regex("\\s+")).lastOrNull().orEmpty()
        }.getOrDefault("")
    }

    private fun binderFocus(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val focusInfo = focusInfoMethod.invoke(atmService) ?: return@runCatching null
            (topActivityField.get(focusInfo) as? ComponentName)?.flattenToString()
        } else {
            val tasks = getTasksMethod.invoke(amService, 1) as? List<*>
            (tasks?.firstOrNull() as? ActivityManager.RunningTaskInfo)
                ?.topActivity
                ?.flattenToString()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun currentLayer(focus: String): List<String> {
        val layers = ArrayList<String>()
        fun consume(line: String) {
            if (!line.contains(focus)) return
            val layer = reSfModern.find(line)?.groupValues?.get(1)?.trim() ?: line.trim()
            if (layer.isEmpty()) return
            if (noiseTokens.none { layer.contains(it) }) layers += layer
        }

        val dumped = binderDumpUsable && runCatching {
            dumpSurfaceFlinger(arrayOf("--list")).use { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { reader ->
                    reader.forEachLine(::consume)
                }
            }
            true
        }.onFailure {
            binderDumpUsable = false
        }.getOrDefault(false)

        if (!dumped) {
            sh("dumpsys SurfaceFlinger --list | grep -F ${shellQuote(focus)}")
                .lineSequence()
                .forEach(::consume)
        }

        layers.sortWith(
            compareByDescending<String> {
                if (it.contains("SurfaceView") && it.contains("BLAST")) 1 else 0
            }.thenByDescending { recencyOf(it) },
        )
        return layers
    }

    private fun recencyOf(layer: String): Int {
        val hash = reHashSuffix.find(layer)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val at = reAtSuffix.find(layer)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        return hash + at
    }

    private fun scanLatencyFps(layer: String, latencySource: Int): Float? {
        if (binderDumpUsable) {
            val result = runCatching {
                val total = fillIoBuffer(arrayOf("--latency", layer))
                parseLatencyFps(ioBuffer, total, latencySource)
            }.onFailure {
                binderDumpUsable = false
            }
            if (result.isSuccess) return result.getOrNull()
        }
        val text = sh("dumpsys SurfaceFlinger --latency ${shellQuote(layer)}")
        if (text.isBlank()) return null
        val bytes = text.toByteArray()
        return parseLatencyFps(bytes, bytes.size, latencySource)
    }

    /**
     * 跳过首行 refresh period，按 tab 切列。
     * [latencySource] 1=第二列 desired present，2=第三列 actual present。
     * 新帧数 / 墙钟间隔 = FPS。
     */
    private fun parseLatencyFps(buf: ByteArray, total: Int, latencySource: Int): Float? {
        val timeNanos = SystemClock.elapsedRealtimeNanos()
        var newFrame = 0
        var maxTs = 0L

        var i = 0
        while (i < total && buf[i] != lf) i++
        i++
        while (i < total) {
            var t1 = i
            while (t1 < total && buf[t1] != tab) t1++
            if (t1 >= total) break
            var t2 = t1 + 1
            while (t2 < total && buf[t2] != tab) t2++
            if (t2 >= total) break
            val colStart: Int
            val colEnd: Int
            if (latencySource == 1) {
                colStart = t1 + 1
                colEnd = t2
            } else {
                var t3 = t2 + 1
                while (t3 < total && buf[t3] != lf && buf[t3] != tab) t3++
                colStart = t2 + 1
                colEnd = t3
            }
            var ts = 0L
            var p = colStart
            while (p < colEnd && buf[p] in digit0..digit9) {
                ts = ts * 10 + (buf[p] - digit0)
                p++
            }
            if (p == colEnd && ts > 0L && ts < Long.MAX_VALUE && ts > maxTs) {
                maxTs = ts
                if (ts > lastLatency) newFrame++
            }
            var nl = colEnd
            while (nl < total && buf[nl] != lf) nl++
            if (nl >= total) break
            i = nl + 1
        }

        if (maxTs == 0L) return null

        if (newFrame == 0) {
            if (++idlePolls >= MAX_IDLE_POLLS) {
                idlePolls = 0
                return null
            }
        } else {
            idlePolls = 0
        }

        val fps = if (lastTimeNanos in 1L..<timeNanos) {
            (newFrame / ((timeNanos - lastTimeNanos) / 1_000_000_000.0)).toFloat()
        } else {
            0f
        }
        lastTimeNanos = timeNanos
        lastLatency = maxTs
        return fps
    }

    private fun dumpSurfaceFlinger(args: Array<String>): ParcelFileDescriptor {
        val service = getService("SurfaceFlinger")
        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe[0]
        val writeFd = pipe[1]
        try {
            service.dumpAsync(writeFd.fileDescriptor, args)
        } catch (e: Exception) {
            runCatching { readFd.close() }
            runCatching { writeFd.close() }
            throw e
        }
        runCatching { writeFd.close() }
        return readFd
    }

    private fun fillIoBuffer(args: Array<String>): Int {
        return ParcelFileDescriptor.AutoCloseInputStream(dumpSurfaceFlinger(args)).use { input ->
            var n = 0
            while (n < ioBuffer.size) {
                val r = input.read(ioBuffer, n, ioBuffer.size - n)
                if (r < 0) break
                n += r
            }
            n
        }
    }

    private fun sh(cmd: String): String {
        val injected = shellExec
        if (injected != null) return injected(cmd)
        return runCatching { shellExecOnce(cmd) }.getOrElse {
            shellStop()
            shellExecOnce(cmd)
        }
    }

    private fun shellStart() {
        val process = ProcessBuilder("/system/bin/sh").start()
        shellProcess = process
        shellWriter = process.outputStream.bufferedWriter()
        shellReader = process.inputStream.bufferedReader()
    }

    private fun shellStop() {
        runCatching { shellWriter?.close() }
        runCatching { shellReader?.close() }
        shellProcess?.destroy()
        shellProcess = null
        shellWriter = null
        shellReader = null
    }

    @Throws(IOException::class)
    private fun shellExecOnce(cmd: String): String {
        val isAlive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            shellProcess?.isAlive == true
        } else {
            shellProcess != null
        }
        if (!isAlive) shellStart()
        val writer = shellWriter!!
        val reader = shellReader!!
        writer.write(cmd)
        writer.newLine()
        writer.write("echo $SHELL_MARKER")
        writer.newLine()
        writer.flush()
        val sb = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: throw IOException("shell exited")
            if (line == SHELL_MARKER) break
            sb.append(line).append('\n')
        }
        return sb.toString().trim()
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
