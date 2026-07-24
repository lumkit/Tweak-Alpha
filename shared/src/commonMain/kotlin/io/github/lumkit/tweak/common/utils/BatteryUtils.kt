package io.github.lumkit.tweak.common.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Clock

/**
 * 电池信息工具类。
 *
 * 通过读取 sysfs 节点获取电池状态信息，底层自动根据运行模式选择
 * Root IPC 或用户态方式读取文件。
 *
 * 所有读取操作共享 500ms 缓存窗口，在窗口期内多次调用不会产生重复 IO。
 */
object BatteryUtils {

    private const val CACHE_DURATION_MS = 500L

    private val mutex = Mutex()

    @Volatile
    private var cachedSnapshot: BatterySnapshot? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    private val ueventPaths = listOf(
        "/sys/class/power_supply/bms/uevent",
        "/sys/class/power_supply/battery/uevent",
        "/sys/class/power_supply/Battery/uevent",
    )

    private val voltagePaths = listOf(
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/Battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
    )

    private val currentPaths = listOf(
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/Battery/current_now",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/battery/current_avg",
        "/sys/class/power_supply/Battery/current_avg",
        "/sys/class/power_supply/bms/current_avg",
        "/sys/class/power_supply/battery/constant_charge_current",
        "/sys/class/power_supply/Battery/constant_charge_current",
        "/sys/class/power_supply/bms/constant_charge_current",
    )

    private val temperaturePaths = listOf(
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/Battery/temp",
        "/sys/class/power_supply/bms/temp",
    )

    private val cycleCountPaths = listOf(
        "/sys/class/power_supply/battery/cycle_count",
        "/sys/class/power_supply/Battery/cycle_count",
        "/sys/class/power_supply/bms/cycle_count",
    )

    private val capacityPaths = listOf(
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/Battery/capacity",
        "/sys/class/power_supply/bms/capacity",
    )

    private val designCapacityPaths = listOf(
        "/sys/class/power_supply/battery/charge_full_design",
        "/sys/class/power_supply/Battery/charge_full_design",
        "/sys/class/power_supply/bms/charge_full_design",
    )

    private val currentCapacityPaths = listOf(
        "/sys/class/power_supply/battery/charge_full",
        "/sys/class/power_supply/Battery/charge_full",
        "/sys/class/power_supply/bms/charge_full",
    )

    /**
     * 获取当前电池电压，单位：mV
     */
    suspend fun getVoltage(): Int? = getSnapshot().voltageMv

    /**
     * 获取当前电池电流，单位：mA
     * 正值表示充电，负值表示放电。
     */
    suspend fun getCurrent(): Int? = getSnapshot().currentMa

    /**
     * 获取当前电池温度，单位：°C
     */
    suspend fun getTemperature(): Float? = getSnapshot().temperatureCelsius

    /**
     * 获取当前电池循环次数。
     */
    suspend fun getCycleCount(): Int? = getSnapshot().cycleCount

    /**
     * 获取当前电池电量百分比（0-100）。
     */
    suspend fun getCapacityPercent(): Int? = getSnapshot().capacityPercent

    /**
     * 获取电池设计容量，单位：mAh
     */
    suspend fun getDesignCapacity(): Int? = getSnapshot().designCapacityMah

    /**
     * 获取电池当前最大容量，单位：mAh
     */
    suspend fun getCurrentFullCapacity(): Int? = getSnapshot().currentFullCapacityMah

    /**
     * 获取当前电池输出功率，单位：mW
     * 计算方式：|电压(mV) * 电流(mA)| / 1000
     */
    suspend fun getPower(): Int? {
        val snapshot = getSnapshot()
        val voltage = snapshot.voltageMv ?: return null
        val current = snapshot.currentMa ?: return null
        return (voltage.toLong() * current.toLong() / 1000L).toInt()
    }

    /**
     * 获取完整的电池快照数据。
     * 受 500ms 缓存保护，窗口期内不会重复读取。
     */
    suspend fun getSnapshot(): BatterySnapshot {
        val now = currentTimeMillis()
        cachedSnapshot?.let { snapshot ->
            if (now - cacheTimestamp < CACHE_DURATION_MS) {
                return snapshot
            }
        }
        return mutex.withLock {
            val nowInLock = currentTimeMillis()
            cachedSnapshot?.let { snapshot ->
                if (nowInLock - cacheTimestamp < CACHE_DURATION_MS) {
                    return snapshot
                }
            }
            val snapshot = readBatterySnapshot()
            cachedSnapshot = snapshot
            cacheTimestamp = currentTimeMillis()
            snapshot
        }
    }

    /**
     * 清除缓存，下次调用将强制重新读取。
     */
    fun invalidateCache() {
        cachedSnapshot = null
        cacheTimestamp = 0L
    }

    private suspend fun readBatterySnapshot(): BatterySnapshot {
        // 优先一次读 uevent（兼容更多 OEM），再回退到单节点 / 平台 API
        val uevent = readUeventProps()

        val designCapacityRaw = uevent?.getLong("POWER_SUPPLY_CHARGE_FULL_DESIGN")
            ?: readFirstAvailable(designCapacityPaths)?.toLongOrNull()
        val currentFullCapacityRaw = uevent?.getLong("POWER_SUPPLY_CHARGE_FULL")
            ?: readFirstAvailable(currentCapacityPaths)?.toLongOrNull()
        val capacityScaleRaw = currentFullCapacityRaw ?: designCapacityRaw
        val capacityDigitLength = capacityScaleRaw?.toString()?.length ?: 0

        val cycleCount = uevent?.getInt("POWER_SUPPLY_CYCLE_COUNT")
            ?: readFirstAvailable(cycleCountPaths)?.toIntOrNull()

        val voltageMv = PlatformBatterySource.getVoltage()
            ?: uevent?.getLong("POWER_SUPPLY_VOLTAGE_NOW")?.let(::normalizeVoltageToMv)
            ?: readFirstAvailable(voltagePaths)?.toLongOrNull()?.let(::normalizeVoltageToMv)

        val temperatureCelsius = PlatformBatterySource.getTemperature()?.let { raw ->
            raw / 10f
        } ?: uevent?.getInt("POWER_SUPPLY_TEMP")?.let(::normalizeTemperatureToCelsius)
            ?: readFirstAvailable(temperaturePaths)?.toIntOrNull()?.let(::normalizeTemperatureToCelsius)

        val currentMa = resolveCurrentMa(uevent, capacityDigitLength)

        val capacity = PlatformBatterySource.getCapacity()
            ?: uevent?.getInt("POWER_SUPPLY_CAPACITY")
            ?: readFirstAvailable(capacityPaths)?.toIntOrNull()

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
     * 解析电流（mA）。
     *
     * 部分设备上 BatteryManager.CURRENT_NOW 会返回 0 表示“不可用”，不能当作真实电流。
     * 参考 Scene 逻辑：优先从 bms/battery 的 uevent 读取 CURRENT_NOW /
     * CONSTANT_CHARGE_CURRENT，并按 charge_full 位数做单位换算。
     */
    private suspend fun resolveCurrentMa(
        uevent: Map<String, String>?,
        capacityDigitLength: Int,
    ): Int? {
        // 平台 API：0 / MIN 视为不可用，继续走 sysfs
        PlatformBatterySource.getCurrentNow()
            ?.takeIf { it != 0L }
            ?.let { return normalizePlatformCurrentToMa(it) }

        val ueventCurrent = uevent?.getLong("POWER_SUPPLY_CURRENT_NOW")
            ?: uevent?.getLong("POWER_SUPPLY_CURRENT_AVG")
            ?: uevent?.getLong("POWER_SUPPLY_CONSTANT_CHARGE_CURRENT")
        ueventCurrent?.let { raw ->
            return normalizeSysfsCurrentToMa(raw, capacityDigitLength)
        }

        readFirstAvailable(currentPaths)?.toLongOrNull()?.let { raw ->
            return normalizeSysfsCurrentToMa(raw, capacityDigitLength)
        }

        return null
    }

    /**
     * Android BatteryManager 约定单位为 µA；少数厂商直接回 mA。
     */
    private fun normalizePlatformCurrentToMa(rawUaOrMa: Long): Int {
        return if (abs(rawUaOrMa) < 10_000L) {
            rawUaOrMa.toInt()
        } else {
            (rawUaOrMa / 1000L).toInt()
        }
    }

    /**
     * Scene 同源换算：以 charge_full(_design) 位数推断电流单位。
     * - 位数 < 5：认为容量是 mAh，电流也是 mA
     * - 位数 >= 5：按 10^(len-4) 缩放（常见 7 位 µAh → 电流 µA / 1000 = mA）
     */
    private fun normalizeSysfsCurrentToMa(raw: Long, capacityDigitLength: Int): Int {
        if (capacityDigitLength in 1..4) {
            return raw.toInt()
        }
        if (capacityDigitLength >= 5) {
            val divisor = 10.0.pow((capacityDigitLength - 4).toDouble())
            return (raw / divisor).toInt()
        }
        return if (abs(raw) > 100_000L) (raw / 1000L).toInt() else raw.toInt()
    }

    private fun normalizeVoltageToMv(raw: Long): Int {
        return when {
            raw > 100_000L -> (raw / 1000L).toInt()
            raw > 10_000L -> (raw / 10L).toInt()
            else -> raw.toInt()
        }
    }

    private fun normalizeTemperatureToCelsius(raw: Int): Float {
        return when {
            raw > 1000 || raw < -1000 -> raw / 1000f
            raw > 200 || raw < -200 -> raw / 10f
            else -> raw.toFloat()
        }
    }

    private fun normalizeCapacityToMah(raw: Long): Int {
        return if (raw > 100_000L) (raw / 1000L).toInt() else raw.toInt()
    }

    private suspend fun readUeventProps(): Map<String, String>? {
        for (path in ueventPaths) {
            val text = when (val result = Files.readText(path)) {
                is NativeFileResult.Success -> result.value
                is NativeFileResult.Failure -> continue
            }
            if (text.isBlank()) continue

            val props = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val info = line.trim()
                if (info.isEmpty()) continue
                val index = info.indexOf('=')
                if (index <= 0 || index >= info.length - 1) continue
                val key = info.substring(0, index).trim()
                val value = info.substring(index + 1).trim()
                // 同名参数只取第一次，避免部分机型重复键值互相覆盖
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    props.putIfAbsent(key, value)
                }
            }
            if (props.isNotEmpty()) return props
        }
        return null
    }

    private fun Map<String, String>.getLong(key: String): Long? =
        this[key]?.toLongOrNull()

    private fun Map<String, String>.getInt(key: String): Int? =
        this[key]?.toIntOrNull()

    private suspend fun readFirstAvailable(paths: List<String>): String? {
        for (path in paths) {
            val result = Files.readText(path)
            when (result) {
                is NativeFileResult.Success -> {
                    val text = result.value.trim()
                    if (text.isNotEmpty()) return text
                }
                is NativeFileResult.Failure -> continue
            }
        }
        return null
    }

    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/**
 * 电池状态快照，所有字段均可能为 null（表示该节点不可读）。
 * @param voltageMv 电压，单位 mV
 * @param currentMa 电流，单位 mA。正值充电，负值放电
 * @param temperatureCelsius 温度，单位 °C
 * @param cycleCount 循环次数
 * @param capacityPercent 电量百分比 0-100
 * @param designCapacityMah 设计容量，单位 mAh
 * @param currentFullCapacityMah 当前最大容量，单位 mAh
 */
@Serializable
data class BatterySnapshot(
    /** 电压，单位 mV */
    val voltageMv: Int?,
    /** 电流，单位 mA。正值充电，负值放电 */
    val currentMa: Int?,
    /** 温度，单位 °C */
    val temperatureCelsius: Float?,
    /** 循环次数 */
    val cycleCount: Int?,
    /** 电量百分比 0-100 */
    val capacityPercent: Int?,
    /** 设计容量，单位 mAh */
    val designCapacityMah: Int?,
    /** 当前最大容量，单位 mAh */
    val currentFullCapacityMah: Int?,
)

/**
 * 平台侧电池数据源，通过系统 API 获取标准化电池信息。
 */
internal expect object PlatformBatterySource {
    /**
     * 获取当前电池电流，单位 µA。
     * 返回 null 表示不可用。
     */
    fun getCurrentNow(): Long?

    /**
     * 获取电池电压，单位 mV。
     * 返回 null 表示不可用。
     */
    fun getVoltage(): Int?

    /**
     * 获取电池温度，单位 0.1°C（需除以10得到摄氏度）。
     * 返回 null 表示不可用。
     */
    fun getTemperature(): Int?

    /**
     * 获取电池电量百分比 0-100。
     * 返回 null 表示不可用。
     */
    fun getCapacity(): Int?
}
