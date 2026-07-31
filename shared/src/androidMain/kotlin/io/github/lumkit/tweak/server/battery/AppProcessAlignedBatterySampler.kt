package io.github.lumkit.tweak.server.battery

import android.os.BatteryManager
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.BatterySysfsPaths
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.sharednative.BatteryBridge
import java.io.File

/**
 * 对齐 BatteryRecorder 的采样器，多源回退：
 * 1. Sysfs 节点 / uevent
 * 2. [BatteryBridge]（BatteryManager / 粘性广播）
 * 3. `dumpsys battery`（FakeContext 下广播常挂，shell 仍可 dumpsys）
 */
class AppProcessAlignedBatterySampler : BatterySampler {

    @Volatile
    private var dumpsysCache: DumpsysBattery? = null

    @Volatile
    private var dumpsysCacheAtMs: Long = 0L

    override fun sample(): BatterySample? {
        dumpsysCache = null
        dumpsysCacheAtMs = 0L

        val level = readCapacity()
        if (level !in 0..100) return null

        val currentUa = readCurrentUa()
        val voltageMv = readVoltageMv()
        val tempCenti = readTempCenti()
        val chargeState = resolveChargeState(currentUa)

        return BatterySample(
            timestampMs = System.currentTimeMillis(),
            level = level,
            voltageMv = voltageMv ?: Int.MIN_VALUE,
            currentMa = BatteryReadingNormalize.normalizeCurrent(currentUa),
            tempCenti = tempCenti ?: Short.MIN_VALUE,
            screenOn = ScreenStateReader.isInteractive(),
            state = chargeState.code,
        )
    }

    private fun readCurrentUa(): Long {
        val fromBridge = BatteryBridge.getCurrentNow()
        if (fromBridge != Int.MIN_VALUE && fromBridge != 0) {
            return fromBridge.toLong()
        }
        for (path in BatterySysfsPaths.currentNowPaths) {
            readLong(path)?.let { return it }
        }
        readUeventLong("POWER_SUPPLY_CURRENT_NOW")?.let { return it }
        return fromBridge.toLong()
    }

    private fun readVoltageMv(): Int? {
        val fromBridge = BatteryBridge.getVoltage()
        if (fromBridge != Int.MIN_VALUE && fromBridge > 0) {
            return fromBridge
        }
        for (path in BatterySysfsPaths.voltageNowPaths) {
            val raw = readLong(path) ?: continue
            val mv = BatteryReadingNormalize.normalizeVoltage(raw)
            if (mv > 0) return mv
        }
        readUeventLong("POWER_SUPPLY_VOLTAGE_NOW")?.let { raw ->
            val mv = BatteryReadingNormalize.normalizeVoltage(raw)
            if (mv > 0) return mv
        }
        dumpsys()?.voltageMv?.takeIf { it > 0 }?.let { return it }
        return null
    }

    private fun readCapacity(): Int {
        for (path in BatterySysfsPaths.capacityPaths) {
            val value = readInt(path)
            if (value != null && value in 0..100) return value
        }
        readUeventInt("POWER_SUPPLY_CAPACITY")?.takeIf { it in 0..100 }?.let { return it }
        val fromBridge = BatteryBridge.getCapacity()
        if (fromBridge in 0..100) return fromBridge
        return dumpsys()?.level?.takeIf { it in 0..100 } ?: -1
    }

    private fun readTempCenti(): Short? {
        val fromBridge = BatteryBridge.getTemperature()
        if (fromBridge != Int.MIN_VALUE) {
            return (fromBridge * 10).toShort()
        }
        for (path in TEMPERATURE_PATHS) {
            val raw = readInt(path) ?: continue
            return tenthsToCenti(raw)
        }
        readUeventInt("POWER_SUPPLY_TEMP")?.let { return tenthsToCenti(it) }
        dumpsys()?.temperatureTenths?.let { return tenthsToCenti(it) }
        return null
    }

    private fun tenthsToCenti(raw: Int): Short {
        val tenths = when {
            raw > 1000 || raw < -1000 -> raw / 100
            else -> raw
        }
        return (tenths * 10).toShort()
    }

    private fun resolveChargeState(currentUa: Long): BatteryChargeState {
        parseStatusText(readFirstSysfsStatus())?.let { return it }
        readUeventString("POWER_SUPPLY_STATUS")?.let { text ->
            parseStatusText(text)?.let { return it }
        }

        val status = BatteryBridge.getStatus().takeIf { it != Int.MIN_VALUE }
            ?: dumpsys()?.status
        if (status != null) {
            return when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargeState.CHARGING
                BatteryManager.BATTERY_STATUS_FULL ->
                    if (isPlugged()) BatteryChargeState.FULL else BatteryChargeState.DISCHARGING
                else -> BatteryChargeState.DISCHARGING
            }
        }

        // 状态全挂时用电流符号兜底（校准前原始 µA）
        return if (currentUa > 0L || isPlugged()) {
            BatteryChargeState.CHARGING
        } else {
            BatteryChargeState.DISCHARGING
        }
    }

    private fun parseStatusText(status: String?): BatteryChargeState? {
        val text = status?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when (text.first().uppercaseChar()) {
            'C' -> BatteryChargeState.CHARGING
            'F' -> if (isPlugged()) BatteryChargeState.FULL else BatteryChargeState.DISCHARGING
            'D', 'N' -> BatteryChargeState.DISCHARGING
            else -> null
        }
    }

    private fun readFirstSysfsStatus(): String? {
        for (path in BatterySysfsPaths.statusPaths) {
            val status = readText(path)?.trim()
            if (!status.isNullOrEmpty()) return status
        }
        return null
    }

    private fun isPlugged(): Boolean {
        if (BatteryBridge.isPlugged()) return true
        for (path in PLUGGED_ONLINE_PATHS) {
            if (readText(path)?.trim() == "1") return true
        }
        dumpsys()?.let { d ->
            if (d.acPowered || d.usbPowered || d.wirelessPowered) return true
            if ((d.plugged ?: 0) != 0) return true
        }
        return false
    }

    private fun dumpsys(): DumpsysBattery? {
        val now = System.currentTimeMillis()
        dumpsysCache?.let { cached ->
            if (now - dumpsysCacheAtMs < DUMPSYS_CACHE_MS) return cached
        }
        val parsed = runCatching { parseDumpsysBattery() }.getOrNull()
        dumpsysCache = parsed
        dumpsysCacheAtMs = now
        return parsed
    }

    private fun parseDumpsysBattery(): DumpsysBattery? {
        val text = Runtime.getRuntime()
            .exec(arrayOf("dumpsys", "battery"))
            .inputStream
            .bufferedReader()
            .use { it.readText() }
        if (text.isBlank()) return null

        fun pickInt(key: String): Int? {
            val regex = Regex("""(?im)^\s*$key\s*:\s*(-?\d+)\s*$""")
            return regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        fun pickBool(key: String): Boolean {
            val regex = Regex("""(?im)^\s*$key\s*:\s*(true|false)\s*$""")
            return regex.find(text)?.groupValues?.getOrNull(1)
                ?.equals("true", ignoreCase = true) == true
        }

        return DumpsysBattery(
            level = pickInt("level"),
            voltageMv = pickInt("voltage"),
            temperatureTenths = pickInt("temperature"),
            status = pickInt("status"),
            plugged = pickInt("plugged"),
            acPowered = pickBool("AC powered"),
            usbPowered = pickBool("USB powered"),
            wirelessPowered = pickBool("Wireless powered"),
        ).also {
            logD(
                "dumpsys fallback level=${it.level} V=${it.voltageMv} T=${it.temperatureTenths} " +
                    "status=${it.status} plugged=${it.plugged}",
                TAG,
            )
        }
    }

    private fun readUeventLong(key: String): Long? {
        for (path in BatterySysfsPaths.ueventPaths) {
            val map = readUevent(path) ?: continue
            map[key]?.toLongOrNull()?.let { return it }
        }
        return null
    }

    private fun readUeventInt(key: String): Int? =
        readUeventLong(key)?.toInt()

    private fun readUeventString(key: String): String? {
        for (path in BatterySysfsPaths.ueventPaths) {
            val map = readUevent(path) ?: continue
            map[key]?.let { return it }
        }
        return null
    }

    private fun readUevent(path: String): Map<String, String>? {
        val text = readText(path) ?: return null
        val out = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            out[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun readText(path: String): String? =
        runCatching { File(path).readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun readLong(path: String): Long? =
        readText(path)?.trim()?.toLongOrNull()

    private fun readInt(path: String): Int? =
        readText(path)?.trim()?.toIntOrNull()

    private data class DumpsysBattery(
        val level: Int?,
        val voltageMv: Int?,
        val temperatureTenths: Int?,
        val status: Int?,
        val plugged: Int?,
        val acPowered: Boolean,
        val usbPowered: Boolean,
        val wirelessPowered: Boolean,
    )

    companion object {
        private const val TAG = "BrBatterySampler"
        private const val DUMPSYS_CACHE_MS = 800L

        private val TEMPERATURE_PATHS = BatterySysfsPaths.temperaturePaths + listOf(
            "/sys/class/power_supply/battery/temp_now",
            "/sys/class/power_supply/Battery/temp_now",
            "/sys/class/power_supply/bms/temp_now",
        )

        private val PLUGGED_ONLINE_PATHS = listOf(
            "/sys/class/power_supply/usb/online",
            "/sys/class/power_supply/ac/online",
            "/sys/class/power_supply/wireless/online",
            "/sys/class/power_supply/pc_port/online",
        )

        init {
            logD("using BR-aligned battery sampler", TAG)
        }
    }
}
