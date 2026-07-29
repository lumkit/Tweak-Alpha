package io.github.lumkit.tweak.server.battery

import io.github.lumkit.tweak.common.daemon.DaemonPaths
import java.io.File

data class BatteryRecordConf(
    val enabled: Boolean = true,
    val intervalMs: Int = 1_000,
    val maxPartBytes: Long = DaemonPaths.DEFAULT_BATTERY_LOG_MAX_PART_BYTES,
) {
    companion object {
        private const val MIN_INTERVAL_MS = 100
        private const val MIN_PART_BYTES = 64L * 1024

        fun read(path: String): BatteryRecordConf {
            val file = File(path)
            if (!file.isFile) return BatteryRecordConf()
            var enabled = true
            var intervalMs = 1_000
            var maxPartBytes = DaemonPaths.DEFAULT_BATTERY_LOG_MAX_PART_BYTES
            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEachLine
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                when (key) {
                    "enabled" -> enabled = value == "1" || value.equals("true", ignoreCase = true)
                    "interval_ms" -> value.toIntOrNull()?.takeIf { it >= MIN_INTERVAL_MS }?.let { intervalMs = it }
                    "max_part_bytes" -> value.toLongOrNull()?.takeIf { it >= MIN_PART_BYTES }?.let { maxPartBytes = it }
                }
            }
            return BatteryRecordConf(
                enabled = enabled,
                intervalMs = intervalMs.coerceAtLeast(MIN_INTERVAL_MS),
                maxPartBytes = maxPartBytes.coerceAtLeast(MIN_PART_BYTES),
            )
        }
    }
}
