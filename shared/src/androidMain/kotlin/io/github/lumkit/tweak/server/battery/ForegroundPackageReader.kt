package io.github.lumkit.tweak.server.battery

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
        private val focusPackageRegex = Regex(
            """(?:mCurrentFocus|mFocusedApp|mFocusedWindow).*?\s+([A-Za-z][\w]*(?:\.[A-Za-z][\w]*)+)(?:/|\s|}|\])""",
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
            val process = ProcessBuilder(
                "sh", "-c",
                "dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 4",
            ).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor(2, TimeUnit.SECONDS)
            runCatching { process.destroyForcibly() }
            return output
        }
    }
}
