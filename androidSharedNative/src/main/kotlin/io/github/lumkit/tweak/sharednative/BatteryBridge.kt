package io.github.lumkit.tweak.sharednative

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder

/**
 * 电池信息桥接（对齐 BatteryRecorder DumpsysSampler 的电流口径）。
 *
 * 电流：**仅** `BATTERY_PROPERTY_CURRENT_NOW`（µA）。
 * 不回退 CURRENT_AVERAGE / 广播 extras（易变成恒流上限 ~8A）。
 */
object BatteryBridge {

    @Volatile
    private var batteryManager: BatteryManager? = null

    @Volatile
    private var lastBatteryStatus: Intent? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var registrar: Any? = null

    @Volatile
    private var propertyCtor: java.lang.reflect.Constructor<*>? = null

    @JvmStatic
    fun init(context: Context) {
        if (batteryManager != null && appContext != null) return
        appContext = context.applicationContext ?: context
        batteryManager = appContext?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        runCatching {
            appContext?.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    lastBatteryStatus = intent
                }
            }, filter)
        }
        lastBatteryStatus = runCatching {
            appContext?.registerReceiver(null, filter)
        }.getOrNull()

        initRegistrar()
    }

    private fun initRegistrar() {
        if (registrar != null) return
        runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val raw = getService.invoke(null, "batteryproperties") as? IBinder ?: return@runCatching
            registrar = Class.forName("android.os.IBatteryPropertiesRegistrar\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, raw)
            propertyCtor = Class.forName("android.os.BatteryProperty").getConstructor()
        }
    }

    /**
     * 当前电池电流，单位 µA。
     * 对齐 BR：优先 [IBatteryPropertiesRegistrar]，再 [BatteryManager]。
     * 返回 [Integer.MIN_VALUE] 表示不可用。
     */
    @JvmStatic
    fun getCurrentNow(): Int {
        batteryManager?.let { bm ->
            val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            return current
        }
        return Int.MIN_VALUE
    }

    @JvmStatic
    fun getCurrentAverage(): Int {
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?: Int.MIN_VALUE
    }

    @JvmStatic
    fun getChargeCounter(): Int {
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?: Int.MIN_VALUE
    }

    @JvmStatic
    fun getCapacity(): Int {
        initRegistrar()
        readRegistrarLong(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.let {
            return it.toInt().coerceIn(0, 100)
        }
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: Int.MIN_VALUE
    }

    @JvmStatic
    fun getEnergyCounter(): Long {
        return batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            ?: Long.MIN_VALUE
    }

    @JvmStatic
    fun getVoltage(): Int {
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }

    @JvmStatic
    fun getTemperature(): Int {
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }

    @JvmStatic
    fun getHealth(): Int {
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }

    @JvmStatic
    fun getStatus(): Int {
        initRegistrar()
        readRegistrarLong(BatteryManager.BATTERY_PROPERTY_STATUS)?.let {
            return it.toInt()
        }
        return lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
    }

    @JvmStatic
    fun isPlugged(): Boolean {
        return (lastBatteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private fun readRegistrarLong(propertyId: Int): Long? {
        val reg = registrar ?: return null
        val ctor = propertyCtor ?: return null
        return runCatching {
            val prop = ctor.newInstance()
            val method = reg.javaClass.methods.firstOrNull {
                it.name == "getProperty" && it.parameterTypes.size == 2
            } ?: return null
            method.invoke(reg, propertyId, prop)
            val getLong = prop.javaClass.methods.firstOrNull {
                it.name == "getLong" && it.parameterTypes.isEmpty()
            } ?: return null
            getLong.invoke(prop) as? Long
        }.getOrNull()
    }
}
