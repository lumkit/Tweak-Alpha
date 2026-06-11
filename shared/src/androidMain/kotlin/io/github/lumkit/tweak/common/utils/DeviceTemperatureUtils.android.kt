package io.github.lumkit.tweak.common.utils

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.github.lumkit.tweak.application
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal actual object PlatformTemperatureSource {
    private var thermalDumpEntries: List<ThermalDumpEntry> = emptyList()
    private var thermalDumpMark: TimeMark? = null

    actual suspend fun getTemperatures(category: TemperatureCategory): List<Pair<String, Float>> {
        return getThermalDumpEntries()
            .filter { it.category == category }
            .map { it.name to it.temperature }
    }

    actual suspend fun getBatteryTemperature(): Float? {
        val batteryIntent = application.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return null
        val rawTemperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (rawTemperature == Int.MIN_VALUE) {
            return null
        }
        return rawTemperature / 10f
    }

    private suspend fun getThermalDumpEntries(): List<ThermalDumpEntry> {
        val cacheMark = thermalDumpMark
        if (cacheMark != null && cacheMark.elapsedNow() < 2.seconds) {
            return thermalDumpEntries
        }

        val result = KernelProps.exec("dumpsys thermalservice")
        if (!result.isSuccess) {
            return thermalDumpEntries
        }

        val parsedEntries = parseThermalDump(result.out.joinToString(separator = "\n"))
        thermalDumpEntries = parsedEntries
        thermalDumpMark = TimeSource.Monotonic.markNow()
        return parsedEntries
    }

    private fun parseThermalDump(dump: String): List<ThermalDumpEntry> {
        if (dump.isBlank()) {
            return emptyList()
        }

        val objectPattern = Regex("""Temperature\{([^}]*)\}""")
        val valuePattern = Regex("""(?:mValue|value)\s*=\s*(-?\d+(?:\.\d+)?)""")
        val typePattern = Regex("""(?:mType|type)\s*=\s*([^,}]+)""")
        val namePattern = Regex("""(?:mName|name)\s*=\s*([^,}]+)""")

        return buildList {
            objectPattern.findAll(dump).forEach { match ->
                val content = match.groupValues[1]
                val rawValue = valuePattern.find(content)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    ?: return@forEach
                val normalizedValue = normalizeTemperatureValue(rawValue) ?: return@forEach
                val rawType = typePattern.find(content)?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.trim('"')
                    ?.trim()
                    .orEmpty()
                val rawName = namePattern.find(content)?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.trim('"')
                    ?.trim()
                    .orEmpty()
                val category = resolveCategory(rawType, rawName) ?: return@forEach
                val displayName = rawName.ifBlank {
                    when (category) {
                        TemperatureCategory.CPU -> "cpu"
                        TemperatureCategory.GPU -> "gpu"
                        TemperatureCategory.BATTERY -> "battery"
                    }
                }
                add(ThermalDumpEntry(category, displayName, normalizedValue))
            }
        }
    }

    private fun resolveCategory(rawType: String, rawName: String): TemperatureCategory? {
        return when (rawType.lowercase()) {
            "0", "cpu" -> TemperatureCategory.CPU
            "1", "gpu" -> TemperatureCategory.GPU
            "2", "battery" -> TemperatureCategory.BATTERY
            else -> {
                val normalizedName = rawName.lowercase()
                when {
                    "cpu" in normalizedName || "soc" in normalizedName -> TemperatureCategory.CPU
                    "gpu" in normalizedName || "kgsl" in normalizedName || "adreno" in normalizedName || "mali" in normalizedName -> TemperatureCategory.GPU
                    "battery" in normalizedName || "bms" in normalizedName || "bat" in normalizedName -> TemperatureCategory.BATTERY
                    else -> null
                }
            }
        }
    }

    private data class ThermalDumpEntry(
        val category: TemperatureCategory,
        val name: String,
        val temperature: Float,
    )
}
