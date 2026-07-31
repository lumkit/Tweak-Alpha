package io.github.lumkit.tweak.common.utils

/**
 * 电池快照读取核心（App [BatteryUtils] 与 tweak_server 共用）。
 *
 * 电流逻辑全量对齐 BatteryRecorder：
 * 1. [SysfsSampler]：读 `battery/current_now` 原始 µA
 * 2. [DumpsysSampler]：仅 `BATTERY_PROPERTY_CURRENT_NOW`（µA）
 * 不混入 CURRENT_AVERAGE / CONSTANT_CHARGE_CURRENT / uevent 投票 / 单位启发式。
 *
 * @param readText 按路径读取 sysfs 文本；失败或空串返回 null。
 */
object BatterySnapshotCore {

    suspend fun readSnapshot(
        readText: suspend (String) -> String?,
    ): BatterySnapshot {
        val designCapacityRaw =
            readFirstAvailable(readText, BatterySysfsPaths.designCapacityPaths)?.toLongOrNull()
        val currentFullCapacityRaw =
            readFirstAvailable(readText, BatterySysfsPaths.currentCapacityPaths)?.toLongOrNull()

        val cycleCount =
            readFirstAvailable(readText, BatterySysfsPaths.cycleCountPaths)?.toIntOrNull()

        // 电压：优先平台（广播 mV），再 sysfs（µV→mV，对齐 BR normalizeVoltage）
        val voltageMv = PlatformBatterySource.getVoltage()
            ?: readFirstAvailable(readText, BatterySysfsPaths.voltageNowPaths)?.toLongOrNull()
                ?.let(BatteryReadingNormalize::microAmpToMilliAmp)

        val temperatureCelsius = PlatformBatterySource.getTemperature()?.let { raw ->
            raw / 10f
        } ?: readFirstAvailable(readText, BatterySysfsPaths.temperaturePaths)?.toIntOrNull()
            ?.let(::normalizeTemperatureToCelsius)

        val currentMa = PlatformBatterySource.getCurrentNow()?.let(BatteryReadingNormalize::microAmpToMilliAmp)

        val capacity = PlatformBatterySource.getCapacity()
            ?: readFirstAvailable(readText, BatterySysfsPaths.capacityPaths)?.toIntOrNull()

        return BatterySnapshot(
            voltageMv = voltageMv,
            currentMa = currentMa,
            temperatureCelsius = temperatureCelsius,
            cycleCount = cycleCount,
            capacityPercent = capacity,
            designCapacityMah = designCapacityRaw?.let(::normalizeCapacityToMah),
            currentFullCapacityMah = currentFullCapacityRaw?.let(::normalizeCapacityToMah),
        )
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

    private fun normalizeTemperatureToCelsius(raw: Int): Float = when {
        raw > 1000 || raw < -1000 -> raw / 1000f
        raw > 200 || raw < -200 -> raw / 10f
        else -> raw.toFloat()
    }

    private fun normalizeCapacityToMah(raw: Long): Int =
        if (raw > 100_000L) (raw / 1000L).toInt() else raw.toInt()
}
