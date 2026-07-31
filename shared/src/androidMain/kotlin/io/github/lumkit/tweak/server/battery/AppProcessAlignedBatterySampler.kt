package io.github.lumkit.tweak.server.battery

import android.os.BatteryManager
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
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
        val chargeState = resolveChargeState()

        return BatterySample(
            timestampMs = System.currentTimeMillis(),
            level = level,
            voltageMv = voltageMv,
            currentMa = BatteryReadingNormalize.normalizeCurrent(currentUa),
            tempCenti = tempCenti ?: Short.MIN_VALUE,
            screenOn = ScreenStateReader.isInteractive(),
            state = chargeState.code,
        )
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

    /**
     * 0 放电 / 1 充电 / 2 充满（插电）。
     * Full 且仍插电 → [BatteryChargeState.FULL]；拔电后的 Full → 放电。
     */
    private fun resolveChargeState(): BatteryChargeState {
        for (path in BatterySysfsPaths.statusPaths) {
            val status = readText(path)?.trim().orEmpty()
            if (status.isEmpty()) continue
            return when (status.first().uppercaseChar()) {
                'C' -> BatteryChargeState.CHARGING
                'F' -> if (BatteryBridge.isPlugged()) {
                    BatteryChargeState.FULL
                } else {
                    BatteryChargeState.DISCHARGING
                }
                else -> BatteryChargeState.DISCHARGING
            }
        }
        val status = BatteryBridge.getStatus()
        if (status == Int.MIN_VALUE) return BatteryChargeState.DISCHARGING
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_FULL ->
                if (BatteryBridge.isPlugged()) {
                    BatteryChargeState.FULL
                } else {
                    BatteryChargeState.DISCHARGING
                }
            else -> BatteryChargeState.DISCHARGING
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
