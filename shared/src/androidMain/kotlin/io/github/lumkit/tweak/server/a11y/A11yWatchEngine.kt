package io.github.lumkit.tweak.server.a11y

import io.github.lumkit.tweak.common.daemon.DaemonPaths
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.logW
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class A11yWatchConf(
    val enabled: Boolean = false,
    val intervalMs: Int = DaemonPaths.DEFAULT_A11Y_INTERVAL_MS,
    val component: String = "",
) {
    companion object {
        fun read(path: String): A11yWatchConf {
            val file = File(path)
            if (!file.isFile) return A11yWatchConf()
            var enabled = false
            var intervalMs = DaemonPaths.DEFAULT_A11Y_INTERVAL_MS
            var component = ""
            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEachLine
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                when (key) {
                    "enabled" -> enabled = value == "1" || value.equals("true", ignoreCase = true)
                    "interval_ms" -> value.toIntOrNull()
                        ?.takeIf { it >= DaemonPaths.MIN_A11Y_INTERVAL_MS }
                        ?.let { intervalMs = it }
                    "component" -> component = value
                }
            }
            return A11yWatchConf(enabled, intervalMs, component)
        }
    }
}

/**
 * 无障碍保活巡检：读 `a11y_watch.conf`，按间隔 `settings put` 确保服务在列表中。
 */
class A11yWatchEngine(
    private val confPath: String,
) {
    private val running = AtomicBoolean(false)
    private val reloadRequested = AtomicBoolean(false)
    private val confRef = AtomicReference(A11yWatchConf())
    private var thread: Thread? = null

    @Volatile
    var lastStatusLine: String = "a11y_enabled=0 a11y_interval_ms=0"
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({
            runCatching { loop() }
                .onFailure { logE("a11y engine fatal: ${it.message}", it, TAG) }
                .also {
                    running.set(false)
                    logD("a11y engine stopped", TAG)
                }
        }, "tweak-a11y").also {
            it.isDaemon = true
            it.start()
        }
        logD("a11y engine started", TAG)
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    fun reloadConfig() {
        reloadRequested.set(true)
        thread?.interrupt()
    }

    private fun loop() {
        while (running.get()) {
            val cfg = A11yWatchConf.read(confPath)
            confRef.set(cfg)
            lastStatusLine =
                "a11y_enabled=${if (cfg.enabled) 1 else 0} a11y_interval_ms=${cfg.intervalMs}"

                if (cfg.enabled && cfg.component.isNotBlank()) {
                runCatching { ensureAccessibilityEnabled(cfg.component) }
                    .onFailure { logW("ensure a11y failed: ${it.message}", TAG) }
                sleepInterruptible(cfg.intervalMs.toLong())
            } else {
                sleepInterruptible(3_000L)
            }
        }
    }

    private fun sleepInterruptible(ms: Long) {
        try {
            var left = ms
            while (left > 0 && running.get()) {
                if (reloadRequested.compareAndSet(true, false)) return
                val slice = minOf(left, 500L)
                Thread.sleep(slice)
                left -= slice
            }
        } catch (_: InterruptedException) {
            reloadRequested.set(false)
        }
    }

    companion object {
        private const val TAG = "A11yWatchEngine"

        fun createDefault(daemonDir: File): A11yWatchEngine =
            A11yWatchEngine(File(daemonDir, DaemonPaths.A11Y_WATCH_CONF_NAME).absolutePath)

        fun ensureAccessibilityEnabled(component: String): Boolean {
            if (component.isBlank()) return false
            var current = execCapture("settings get secure enabled_accessibility_services").trim()
            if (current == "null") current = ""
            if (!current.contains(component)) {
                val newValue = if (current.isNotEmpty()) "$current:$component" else component
                val code = execCode(
                    "settings put secure enabled_accessibility_services ${shellQuote(newValue)}",
                )
                if (code != 0) {
                    logE("settings put enabled_accessibility_services failed code=$code", null, TAG)
                    return false
                }
                logD("enabled_accessibility_services=$newValue", TAG)
            }
            execCode("settings put secure accessibility_enabled 1")

            var miui = execCapture("settings get secure permitted_accessibility_services").trim()
            if (miui == "null") miui = ""
            if (miui.isNotEmpty() && !miui.contains(component)) {
                val miuiNew = if (miui.isNotEmpty()) "$miui:$component" else component
                if (execCode(
                        "settings put secure permitted_accessibility_services ${shellQuote(miuiNew)}",
                    ) == 0
                ) {
                    logD("permitted_accessibility_services=$miuiNew", TAG)
                }
            }
            return true
        }

        private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

        private fun execCapture(cmd: String): String {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            return out
        }

        private fun execCode(cmd: String): Int {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            p.inputStream.bufferedReader().readText()
            return p.waitFor()
        }
    }
}
