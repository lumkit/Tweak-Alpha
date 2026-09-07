package io.github.lumkit.tweak.server.fps

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException

/**
 * 对齐 Frame.kt：在特权进程内用 Hidden API + SurfaceFlinger dumpAsync 采样 FPS。
 * [shellExec] 仅用于 dumpAsync 失败时的 dumpsys 回退；服务端默认保活 `/system/bin/sh`。
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
    private var lastFrameTime = 0L
    private var lastFps = 0f
    private var idlePolls = 0
    private var binderDumpUsable = true

    private val tab: Byte = 9
    private val space: Byte = 32
    private val lf: Byte = 10
    private val cr: Byte = 13
    private val digit0: Byte = 48
    private val digit9: Byte = 57
    private val ioBuffer = ByteArray(64 * 1024)

    @Synchronized
    fun currentFps(): Float {
        if (lastLayer.isNotEmpty()) {
            val fps = scanLatencyFps(lastLayer)
            if (fps != null) {
                lastFps = fps
                return fps
            }
            lastLayer = ""
        }
        val focus = currentFocus()
        if (focus.isEmpty()) {
            lastFocus = ""
            lastLayer = ""
            return lastFps
        }
        lastFocus = focus
        val layer = currentLayer(focus).firstOrNull() ?: return lastFps
        if (layer != lastLayer) {
            lastFrameTime = 0L
            idlePolls = 0
        }
        val fps = scanLatencyFps(layer)
        if (fps != null) {
            lastLayer = layer
            lastFps = fps
            return fps
        }
        return lastFps
    }

    private fun currentFocus(): String {
        binderFocus()?.let { return it }
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

    private fun scanLatencyFps(layer: String): Float? {
        if (binderDumpUsable) {
            val result = runCatching {
                val total = fillIoBuffer(arrayOf("--latency", layer))
                parseLatencyFps(ioBuffer, total)
            }.onFailure {
                binderDumpUsable = false
            }
            if (result.isSuccess) return result.getOrNull()
        }
        val text = sh("dumpsys SurfaceFlinger --latency ${shellQuote(layer)}")
        if (text.isBlank()) return null
        val bytes = text.toByteArray()
        return parseLatencyFps(bytes, bytes.size)
    }

    /**
     * 与旧 SurfaceFlingerFpsUtil.calculateFps 一致：
     * 取 latency 第三列（actual present），用新帧时间跨度算 FPS，而不是墙钟 / 第二列。
     */
    private fun parseLatencyFps(buf: ByteArray, total: Int): Float? {
        val timestamps = ArrayList<Long>(128)
        var i = 0
        while (i < total && buf[i] != lf) i++
        i++
        while (i < total) {
            val lineStart = i
            val appTs = parseLongField(buf, total, i)
            val desiredTs = appTs?.let { parseLongField(buf, total, it.second) }
            val actualTs = desiredTs?.let { parseLongField(buf, total, it.second) }
            if (actualTs != null) {
                val actualPresent = actualTs.first
                if (actualPresent > 0L && actualPresent < Long.MAX_VALUE && actualPresent >= lastFrameTime) {
                    timestamps += actualPresent
                }
                i = skipToNextLine(buf, total, actualTs.second)
            } else {
                i = skipToNextLine(buf, total, lineStart)
            }
            if (i <= lineStart) break
        }

        if (timestamps.isEmpty()) {
            if (lastFrameTime == 0L) return null
            if (++idlePolls >= MAX_IDLE_POLLS) {
                idlePolls = 0
                return null
            }
            return lastFps
        }
        idlePolls = 0

        val start = if (timestamps.size == 1) {
            lastFrameTime.takeIf { it > 0L } ?: timestamps.first()
        } else {
            timestamps.first()
        }
        val end = timestamps.last()
        val durationNs = end - start
        lastFrameTime = end
        if (durationNs <= 0L) return lastFps
        return timestamps.size * 1_000_000_000f / durationNs
    }

    private fun parseLongField(buf: ByteArray, total: Int, start: Int): Pair<Long, Int>? {
        var i = start
        while (i < total && isWs(buf[i]) && buf[i] != lf) i++
        if (i >= total || buf[i] == lf || buf[i] !in digit0..digit9) return null
        var value = 0L
        while (i < total && buf[i] in digit0..digit9) {
            value = value * 10 + (buf[i] - digit0)
            i++
        }
        return value to i
    }

    private fun skipToNextLine(buf: ByteArray, total: Int, start: Int): Int {
        var i = start
        while (i < total && buf[i] != lf) i++
        if (i < total && buf[i] == lf) i++
        return i
    }

    private fun isWs(b: Byte): Boolean = b == tab || b == space || b == cr

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
