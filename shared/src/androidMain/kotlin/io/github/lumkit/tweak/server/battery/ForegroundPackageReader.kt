package io.github.lumkit.tweak.server.battery

import android.os.Build
import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 前台包名读取：短 TTL 缓存，miss 时才 dumpsys，失败返回空串。
 */
class ForegroundPackageReader(
    private val ttlMs: Long = 1_500L,
    private val dump: () -> String = ::defaultDumpsysWindowFocus,
) {
    @Volatile
    private var cached: String = ""

    @Volatile
    private var cachedAt: Long = 0L

    fun resolve(): String {
        val now = SystemClock.uptimeMillis()
        if (cached.isNotEmpty() && now - cachedAt <= ttlMs) {
            return cached
        }
        val pkg = runCatching { parseFocusPackage(dump()) }.getOrDefault("").orEmpty()
        cached = pkg
        cachedAt = now
        return pkg
    }

    companion object {
        // Android ICU 对未转义 `}` / 部分 `]` 语法敏感，结尾只用 `/` 或空白截断包名
        private val focusPackageRegex = Regex(
            """(?:mCurrentFocus|mFocusedApp|mFocusedWindow).*?([A-Za-z][\w]*(?:\.[A-Za-z][\w]*)+)(?:/|\s)""",
        )
        private val packageOnlyRegex = Regex(
            """([A-Za-z][\w]*(?:\.[A-Za-z][\w]*)+)""",
        )

        fun parseFocusPackage(dump: String): String {
            if (dump.isBlank()) return ""
            focusPackageRegex.find(dump)?.groupValues?.getOrNull(1)?.let { return it }
            // 兜底：取含点号的 token
            for (line in dump.lineSequence()) {
                if (!line.contains("Focus", ignoreCase = true) &&
                    !line.contains("focused", ignoreCase = true)
                ) {
                    continue
                }
                packageOnlyRegex.findAll(line)
                    .map { it.groupValues[1] }
                    .firstOrNull { it.contains('.') && !it.startsWith("com.android.server") }
                    ?.let { return it }
            }
            return ""
        }

        private fun defaultDumpsysWindowFocus(): String {
            val keywords = arrayOf("mCurrentFocus", "mFocusedApp", "mFocusedWindow")
            return try {
                val process = ProcessBuilder("dumpsys", "window")
                    .redirectErrorStream(true)
                    .start()

                val result = StringBuilder()
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        if (keywords.any { line.contains(it) }) {
                            result.appendLine(line)
                            // 防止部分ROM输出太多
                            if (result.length > 4096) {
                                return@useLines
                            }
                        }
                    }
                }

                waitProcess(process, 2000)
                runCatching {
                    process.destroy()
                }
                result.toString()
            } catch (e: Exception) {
                ""
            }
        }

        private fun waitProcess(
            process: Process,
            timeoutMs: Long
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            }

            val start = System.currentTimeMillis()
            while (true) {
                try {
                    process.exitValue()
                    return true
                } catch (_: IllegalThreadStateException) {
                    // 还在运行
                }

                if (System.currentTimeMillis() - start >= timeoutMs) {
                    return false
                }
                Thread.sleep(10)
            }
        }
    }
}
