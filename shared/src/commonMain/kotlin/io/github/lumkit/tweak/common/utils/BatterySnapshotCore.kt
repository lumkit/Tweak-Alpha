package io.github.lumkit.tweak.common.utils

/**
 * 电池快照读取核心（App [BatteryUtils] 与 tweak_server 共用）。
 *
 * @param readText 按路径读取 sysfs 文本；失败或空串返回 null。
 */
object BatterySnapshotCore {

    suspend fun readSnapshot(
        readText: suspend (String) -> String?,
    ): BatterySnapshot {
        val uevent = readUeventProps(readText)

        val designCapacityRaw = uevent?.getLong("POWER_SUPPLY_CHARGE_FULL_DESIGN")
            ?: readFirstAvailable(readText, BatterySysfsPaths.designCapacityPaths)?.toLongOrNull()
        val currentFullCapacityRaw = uevent?.getLong("POWER_SUPPLY_CHARGE_FULL")
            ?: readFirstAvailable(readText, BatterySysfsPaths.currentCapacityPaths)?.toLongOrNull()

        val cycleCount = uevent?.getInt("POWER_SUPPLY_CYCLE_COUNT")
            ?: readFirstAvailable(readText, BatterySysfsPaths.cycleCountPaths)?.toIntOrNull()

        val voltageMv = PlatformBatterySource.getVoltage()
            ?: uevent?.getLong("POWER_SUPPLY_VOLTAGE_NOW")?.let(::normalizeVoltageToMv)
            ?: readFirstAvailable(readText, BatterySysfsPaths.voltagePaths)?.toLongOrNull()
                ?.let(::normalizeVoltageToMv)

        val temperatureCelsius = PlatformBatterySource.getTemperature()?.let { raw ->
            raw / 10f
        } ?: uevent?.getInt("POWER_SUPPLY_TEMP")?.let(::normalizeTemperatureToCelsius)
            ?: readFirstAvailable(readText, BatterySysfsPaths.temperaturePaths)?.toIntOrNull()
                ?.let(::normalizeTemperatureToCelsius)

        val currentMa = resolveCurrentMa(readText, uevent)

        val capacity = PlatformBatterySource.getCapacity()
            ?: uevent?.getInt("POWER_SUPPLY_CAPACITY")
            ?: readFirstAvailable(readText, BatterySysfsPaths.capacityPaths)?.toIntOrNull()

        val designCapacityMah = designCapacityRaw?.let(::normalizeCapacityToMah)
        val currentFullCapacityMah = currentFullCapacityRaw?.let(::normalizeCapacityToMah)

        return BatterySnapshot(
            voltageMv = voltageMv,
            currentMa = currentMa,
            temperatureCelsius = temperatureCelsius,
            cycleCount = cycleCount,
            capacityPercent = capacity,
            designCapacityMah = designCapacityMah,
            currentFullCapacityMah = currentFullCapacityMah,
        )
    }

    /**
     * 对齐 BatteryRecorder：优先 `BATTERY_PROPERTY_CURRENT_NOW`，再 sysfs `current_now`（µA→mA）。
     * 不使用 `CONSTANT_CHARGE_CURRENT`（常为恒流上限，会导致 8000mA 与 0 交替）。
     */
    private suspend fun resolveCurrentMa(
        readText: suspend (String) -> String?,
        uevent: Map<String, String>?,
    ): Int? {
        PlatformBatterySource.getCurrentNow()
            ?.takeIf { it != 0L }
            ?.let { raw ->
                return BatteryReadingNormalize.coercePlausibleCurrentMa(
                    BatteryReadingNormalize.platformCurrentToMa(raw),
                )
            }

        readFirstAvailable(readText, BatterySysfsPaths.currentNowPaths)
            ?.toLongOrNull()
            ?.let { raw ->
                return BatteryReadingNormalize.coercePlausibleCurrentMa(
                    BatteryReadingNormalize.kernelCurrentNowToMa(raw),
                )
            }

        uevent?.getLong("POWER_SUPPLY_CURRENT_NOW")?.let { raw ->
            return BatteryReadingNormalize.coercePlausibleCurrentMa(
                BatteryReadingNormalize.kernelCurrentNowToMa(raw),
            )
        }

        PlatformBatterySource.getCurrentAverage()
            ?.takeIf { it != 0L }
            ?.let { raw ->
                return BatteryReadingNormalize.coercePlausibleCurrentMa(
                    BatteryReadingNormalize.platformCurrentToMa(raw),
                )
            }

        readFirstAvailable(readText, BatterySysfsPaths.currentAvgPaths)
            ?.toLongOrNull()
            ?.let { raw ->
                return BatteryReadingNormalize.coercePlausibleCurrentMa(
                    BatteryReadingNormalize.kernelCurrentNowToMa(raw),
                )
            }

        uevent?.getLong("POWER_SUPPLY_CURRENT_AVG")?.let { raw ->
            return BatteryReadingNormalize.coercePlausibleCurrentMa(
                BatteryReadingNormalize.kernelCurrentNowToMa(raw),
            )
        }

        return null
    }

    private suspend fun readUeventProps(
        readText: suspend (String) -> String?,
    ): Map<String, String>? {
        for (path in BatterySysfsPaths.ueventPaths) {
            val text = readText(path) ?: continue
            if (text.isBlank()) continue

            val props = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val info = line.trim()
                if (info.isEmpty()) continue
                val index = info.indexOf('=')
                if (index <= 0 || index >= info.length - 1) continue
                val key = info.substring(0, index).trim()
                val value = info.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    props.putIfAbsent(key, value)
                }
            }
            if (props.isNotEmpty()) return props
        }
        return null
    }

    private suspend fun readFirstAvailable(
        readText: suspend (String) -> String?,
        paths: List<String>,
    ): String? {
        for (path in paths) {
            val text = readText(path)?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    private fun normalizeVoltageToMv(raw: Long): Int = when {
        raw > 100_000L -> (raw / 1000L).toInt()
        raw > 10_000L -> (raw / 10L).toInt()
        else -> raw.toInt()
    }

    private fun normalizeTemperatureToCelsius(raw: Int): Float = when {
        raw > 1000 || raw < -1000 -> raw / 1000f
        raw > 200 || raw < -200 -> raw / 10f
        else -> raw.toFloat()
    }

    private fun normalizeCapacityToMah(raw: Long): Int =
        if (raw > 100_000L) (raw / 1000L).toInt() else raw.toInt()

    private fun Map<String, String>.getLong(key: String): Long? =
        this[key]?.toLongOrNull()

    private fun Map<String, String>.getInt(key: String): Int? =
        this[key]?.toIntOrNull()
}
