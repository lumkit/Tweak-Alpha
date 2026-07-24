package io.github.lumkit.tweak.sharednative

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 电池信息桥接类，通过 Android BatteryManager API 和 BroadcastReceiver 获取标准化电池数据。
 * BatteryManager 已对各厂商数据做了归一化处理，比直接读 sysfs 更可靠。
 */
object BatteryBridge {

    @Volatile
    private var batteryManager: BatteryManager? = null

    @Volatile
    private var lastBatteryStatus: Intent? = null

    @Volatile
    private var appContext: Context? = null

    /**
     * 初始化 BatteryManager 实例。需要在 Application 初始化时调用。
     */
    @JvmStatic
    fun init(context: Context) {
        if (batteryManager == null) {
            appContext = context.applicationContext
            batteryManager = appContext?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            // 注册电池状态广播接收器
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            appContext?.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    lastBatteryStatus = intent
                }
            }, filter)

            // 立即获取一次当前电池状态
            lastBatteryStatus = appContext?.registerReceiver(null, filter)
        }
    }

    /**
     * 获取当前电池电流，单位 µA。
     * 正值表示充电电流，负值表示放电电流。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getCurrentNow(): Int {
        batteryManager?.let { bm ->
            val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            // 部分 OEM 在不支持时返回 0 而非 MIN_VALUE，0 交给上层回退 sysfs
            if (current != Int.MIN_VALUE && current != 0) {
                return current
            }
            val average = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            if (average != Int.MIN_VALUE && average != 0) {
                return average
            }
        }

        // BatteryManager 不可用时，尝试从广播获取（部分三星设备需要）
        lastBatteryStatus?.let { intent ->
            if (intent.hasExtra("current_now")) {
                // 三星和部分厂商在广播中提供 current_now，单位通常是 µA
                val current = intent.getIntExtra("current_now", Int.MIN_VALUE)
                if (current != Int.MIN_VALUE && current != 0) {
                    return current
                }
            }
        }

        return Int.MIN_VALUE
    }

    /**
     * 获取平均电池电流，单位 µA。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getCurrentAverage(): Int {
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?: Int.MIN_VALUE
    }

    /**
     * 获取剩余电量，单位 µAh。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getChargeCounter(): Int {
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?: Int.MIN_VALUE
    }

    /**
     * 获取电池剩余容量百分比 (0-100)。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getCapacity(): Int {
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: Int.MIN_VALUE
    }

    /**
     * 获取电池能量计数器，单位 nWh（纳瓦时）。
     * 返回 Long.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getEnergyCounter(): Long {
        return batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            ?: Long.MIN_VALUE
    }

    /**
     * 获取电池电压，单位 mV（毫伏）。
     * 从 ACTION_BATTERY_CHANGED 广播获取。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getVoltage(): Int {
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }

    /**
     * 获取电池温度，单位 0.1°C（需除以10得到摄氏度）。
     * 从 ACTION_BATTERY_CHANGED 广播获取。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getTemperature(): Int {
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }

    /**
     * 获取电池健康状态。
     * 返回值为 BatteryManager.BATTERY_HEALTH_* 常量之一。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getHealth(): Int {
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }

    /**
     * 获取充电状态。
     * 返回值为 BatteryManager.BATTERY_STATUS_* 常量之一。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    @JvmStatic
    fun getStatus(): Int {
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }
}
