package io.github.lumkit.tweak.server.battery

import android.system.Os
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logW
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/**
 * 直接读 power_supply sysfs / uevent。Root 可用；shell 常被 SELinux 拒绝。
 */
class SysfsBatterySampler private constructor(
    private val ueventPath: String,
) : BatterySampler {

    override fun sample(): BatterySample? {
        val uevent = runCatching { File(ueventPath).readText() }.getOrNull().orEmpty()
        if (uevent.isBlank()) return null

        val design = ueventValue(uevent, "POWER_SUPPLY_CHARGE_FULL_DESIGN")
            ?.toLongOrNull()
            ?: ueventValue(uevent, "POWER_SUPPLY_CHARGE_FULL")?.toLongOrNull()
            ?: 0L
        val capDigits = if (design > 0) design.toString().length else 0

        val level = ueventValue(uevent, "POWER_SUPPLY_CAPACITY")?.toIntOrNull() ?: return null
        if (level !in 0..100) return null

        val voltageRaw = ueventValue(uevent, "POWER_SUPPLY_VOLTAGE_NOW")?.toLongOrNull()
            ?: readFirstLineLong("/sys/class/power_supply/battery/voltage_now")
            ?: readFirstLineLong("/sys/class/power_supply/Battery/voltage_now")
        val currentRaw = ueventValue(uevent, "POWER_SUPPLY_CURRENT_NOW")?.toLongOrNull()
        val tempRaw = ueventValue(uevent, "POWER_SUPPLY_TEMP")?.toLongOrNull()
        val status = ueventValue(uevent, "POWER_SUPPLY_STATUS").orEmpty()
        val plugged =
            readFirstLine("/sys/class/power_supply/usb/online") == "1" ||
                readFirstLine("/sys/class/power_supply/ac/online") == "1" ||
                readFirstLine("/sys/class/power_supply/wireless/online") == "1" ||
                ueventValue(uevent, "POWER_SUPPLY_ONLINE") == "1"
        val charging = when {
            status.contains("Charging", ignoreCase = true) -> true
            status.contains("Full", ignoreCase = true) -> plugged
            else -> false
        }

        return BatterySample(
            timestampMs = System.currentTimeMillis(),
            level = level,
            voltageMv = voltageRaw?.let { normalizeVoltageMv(it) } ?: Int.MIN_VALUE,
            currentMa = currentRaw?.let { normalizeCurrentMa(it, capDigits) } ?: Int.MIN_VALUE,
            tempCenti = tempRaw?.let { normalizeTempCenti(it) } ?: Short.MIN_VALUE,
            screenOn = ScreenStateReader.isInteractive(),
            state = if (charging) 1 else 0,
        )
    }

    companion object {
        private const val TAG = "SysfsBatterySampler"

        private val UEVENT_CANDIDATES = listOf(
            "/sys/class/power_supply/bms/uevent",
            "/sys/class/power_supply/battery/uevent",
            "/sys/class/power_supply/Battery/uevent",
        )

        fun tryCreate(): SysfsBatterySampler? {
            if (Os.getuid() == 2000) {
                logD("skip sysfs for shell uid", TAG)
                return null
            }
            for (path in UEVENT_CANDIDATES) {
                val f = File(path)
                if (!f.isFile) continue
                val text = runCatching { f.readText() }.getOrNull().orEmpty()
                val level = ueventValue(text, "POWER_SUPPLY_CAPACITY")?.toIntOrNull()
                if (level != null && level in 0..100) {
                    logD("using uevent=$path", TAG)
                    return SysfsBatterySampler(path)
                }
            }
            logW("no readable battery uevent", TAG)
            return null
        }

        private fun ueventValue(uevent: String, key: String): String? {
            val needle = "$key="
            val idx = uevent.indexOf(needle)
            if (idx < 0) return null
            val start = idx + needle.length
            val end = uevent.indexOf('\n', start).let { if (it < 0) uevent.length else it }
            return uevent.substring(start, end).trim().ifEmpty { null }
        }

        private fun readFirstLineLong(path: String): Long? =
            runCatching { File(path).bufferedReader().readLine()?.trim()?.toLongOrNull() }.getOrNull()

        private fun readFirstLine(path: String): String? =
            runCatching { File(path).bufferedReader().readLine()?.trim() }.getOrNull()

        fun normalizeVoltageMv(raw: Long): Int = when {
            raw > 100_000L -> (raw / 1000L).toInt()
            raw > 10_000L -> (raw / 10L).toInt()
            else -> raw.toInt()
        }

        fun normalizeCurrentMa(raw: Long, capacityDigitLen: Int): Int = when {
            capacityDigitLen in 1..4 -> raw.toInt()
            capacityDigitLen >= 5 -> (raw / 10.0.pow(capacityDigitLen - 4)).toInt()
            abs(raw) > 100_000L -> (raw / 1000L).toInt()
            else -> raw.toInt()
        }

        fun normalizeTempCenti(raw: Long): Short {
            val c = when {
                abs(raw) > 1000L -> raw / 1000.0
                abs(raw) > 200L -> raw / 10.0
                else -> raw.toDouble()
            }
            return (c * 100.0).toInt().toShort()
        }
    }
}
