package io.github.lumkit.tweak.server.battery

import android.os.BatteryManager
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.BatterySysfsPaths
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.sharednative.BatteryBridge
import java.io.File

/**
 * 对齐 BatteryRecorder 的采样器：
 * - 优先 SysfsSampler 口径：直接读 `battery/current_now` 等节点（µA）
 * - 失败则 DumpsysSampler 口径：`BatteryBridge.getCurrentNow()` → registrar/BatteryManager
 */
class AppProcessAlignedBatterySampler : BatterySampler {

    override fun sample(): BatterySample? {
        val level = readCapacity()
        if (level !in 0..100) return null

        val currentUa = readCurrentUa()
        val voltageMv = readVoltageMv()
        val tempCenti = readTempCenti()
        val charging = isCharging()

        return BatterySample(
            timestampMs = System.currentTimeMillis(),
            level = level,
            voltageMv = voltageMv,
            currentMa = currentUa.let { BatteryReadingNormalize.microAmpToMilliAmp(it) },
            tempCenti = tempCenti ?: Short.MIN_VALUE,
            screenOn = ScreenStateReader.isInteractive(),
            state = if (charging) 1 else 0,
        ).also {
            println("AppProcessAlignedBatterySampler: battery sample=$it")
        }
    }

    /** BR SysfsSampler → DumpsysSampler */
    private fun readCurrentUa(): Long {
        return BatteryBridge.getCurrentNow().toLong()
    }

    private fun readVoltageMv(): Int {
        return BatteryBridge.getVoltage()
    }

    private fun readCapacity(): Int {
        return BatteryBridge.getCapacity()
    }

    private fun readTempCenti(): Short? {
        val fromBridge = BatteryBridge.getTemperature()
        if (fromBridge != Int.MIN_VALUE) {
            return (fromBridge * 10).toShort()
        }
        return null
    }

    private fun isCharging(): Boolean {
        for (path in BatterySysfsPaths.statusPaths) {
            val status = readText(path)?.trim().orEmpty()
            if (status.isEmpty()) continue
            // BR：status 首字符 C/D/N/F
            return when (status.first().uppercaseChar()) {
                'C' -> true
                'F' -> BatteryBridge.isPlugged()
                else -> false
            }
        }
        val status = BatteryBridge.getStatus()
        if (status == Int.MIN_VALUE) return false
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> true
            BatteryManager.BATTERY_STATUS_FULL -> BatteryBridge.isPlugged()
            else -> false
        }
    }

    private fun readText(path: String): String? =
        runCatching { File(path).readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun readLong(path: String): Long? =
        readText(path)?.trim()?.toLongOrNull()

    private fun readInt(path: String): Int? =
        readText(path)?.trim()?.toIntOrNull()

    companion object {
        init {
            logD("using BR-aligned battery sampler", "BrBatterySampler")
        }
    }
}
