package io.github.lumkit.tweak.common.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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

    private val voltagePaths = listOf(
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/Battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
    )

    private val currentPaths = listOf(
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/Battery/current_now",
        "/sys/class/power_supply/bms/current_now",
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
        val snapshot = getSnapshot() ?: return null
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
        val voltageRaw = readFirstAvailable(voltagePaths)?.toLongOrNull()
        val tempRaw = readFirstAvailable(temperaturePaths)?.toIntOrNull()
        val cycleCount = readFirstAvailable(cycleCountPaths)?.toIntOrNull()
        val capacity = readFirstAvailable(capacityPaths)?.toIntOrNull()
        val designCapacityRaw = readFirstAvailable(designCapacityPaths)?.toLongOrNull()
        val currentFullCapacityRaw = readFirstAvailable(currentCapacityPaths)?.toLongOrNull()

        // 通过 BatteryManager API 获取标准化电流值（µA），比 sysfs 更可靠
        val batteryCurrentUa = PlatformBatterySource.getCurrentNow()
        val currentMa = if (batteryCurrentUa != null) {
            (batteryCurrentUa / 1000).toInt()
        } else {
            // 回退到 sysfs 读取
            val currentRaw = readFirstAvailable(currentPaths)?.toLongOrNull()
            currentRaw?.let { raw ->
                if (raw > 100_000 || raw < -100_000) (raw / 1000).toInt() else raw.toInt()
            }
        }

        // voltage_now 单位通常是 µV，转换为 mV
        val voltageMv = voltageRaw?.let { raw ->
            if (raw > 100_000) (raw / 1000).toInt() else raw.toInt()
        }

        // temp 单位通常是 0.1°C
        val temperatureCelsius = tempRaw?.let { raw ->
            if (raw > 1000 || raw < -1000) raw / 1000f
            else if (raw > 200 || raw < -200) raw / 10f
            else raw.toFloat()
        }

        // charge_full_design 单位通常是 µAh，转换为 mAh
        val designCapacityMah = designCapacityRaw?.let { raw ->
            if (raw > 100_000) (raw / 1000).toInt() else raw.toInt()
        }

        // charge_full 单位通常是 µAh，转换为 mAh
        val currentFullCapacityMah = currentFullCapacityRaw?.let { raw ->
            if (raw > 100_000) (raw / 1000).toInt() else raw.toInt()
        }

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
}
