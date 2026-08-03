package io.github.lumkit.tweak.server.battery

import io.github.lumkit.tweak.common.utils.battery.UidPowerMath
import io.github.lumkit.tweak.common.utils.battery.UidpowEntry
import io.github.lumkit.tweak.common.utils.battery.UidpowFrame
import io.github.lumkit.tweak.common.utils.logE
import java.io.BufferedInputStream
import java.util.concurrent.TimeUnit

/**
 * 低频抓取 `dumpsys batterystats` 的 Estimated power use 段。
 */
class UidPowerSampler(
    private val timeoutSec: Long = 20L,
    private val maxBytes: Int = 8 * 1024 * 1024,
) {
    fun capture(capturedAt: Long = System.currentTimeMillis()): UidpowFrame? {
        val raw = dumpRaw() ?: return null
        val map = UidPowerMath.parseEstimatedPower(raw)
        if (map.isEmpty()) return null
        return UidpowFrame(
            capturedAt = capturedAt,
            entries = map.values.map { reading ->
                UidpowEntry(
                    uid = reading.uid,
                    packageName = null,
                    totalMah = reading.totalMah,
                    fgMah = reading.fgMah,
                    bgMah = reading.bgMah,
                    fgsMah = reading.fgsMah,
                    cachedMah = reading.cachedMah,
                )
            },
        )
    }

    private fun dumpRaw(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "batterystats"))
            try {
                val bytes = BufferedInputStream(process.inputStream).use { input ->
                    val buf = ByteArray(maxBytes)
                    var offset = 0
                    while (offset < buf.size) {
                        val read = input.read(buf, offset, buf.size - offset)
                        if (read <= 0) break
                        offset += read
                    }
                    if (offset <= 0) ByteArray(0) else buf.copyOf(offset)
                }
                val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    logE("dumpsys batterystats timeout", tag = TAG)
                    return null
                }
                if (bytes.isEmpty()) return null
                String(bytes, Charsets.UTF_8)
            } finally {
                runCatching { process.destroyForcibly() }
            }
        } catch (e: Exception) {
            logE("dumpsys batterystats failed: ${e.message}", e, TAG)
            null
        }
    }

    companion object {
        private const val TAG = "UidPowerSampler"
    }
}
