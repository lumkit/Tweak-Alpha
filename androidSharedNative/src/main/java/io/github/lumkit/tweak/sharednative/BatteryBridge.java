package io.github.lumkit.tweak.sharednative;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * 电池信息桥接类，通过 Android BatteryManager API 和 BroadcastReceiver 获取标准化电池数据。
 * BatteryManager 已对各厂商数据做了归一化处理，比直接读 sysfs 更可靠。
 */
public final class BatteryBridge {

    private BatteryBridge() {
    }

    private static volatile BatteryManager batteryManager;
    private static volatile Intent lastBatteryStatus;
    private static volatile Context appContext;

    /**
     * 初始化 BatteryManager 实例。需要在 Application 初始化时调用。
     */
    public static void init(Context context) {
        if (batteryManager == null) {
            appContext = context.getApplicationContext();
            batteryManager = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
            
            // 注册电池状态广播接收器
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            appContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    lastBatteryStatus = intent;
                }
            }, filter);
            
            // 立即获取一次当前电池状态
            lastBatteryStatus = appContext.registerReceiver(null, filter);
        }
    }

    /**
     * 获取当前电池电流，单位 µA。
     * 正值表示充电电流，负值表示放电电流。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getCurrentNow() {
        BatteryManager bm = batteryManager;
        if (bm != null) {
            int current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (current != Integer.MIN_VALUE) {
                return current;
            }
        }
        
        // BatteryManager 不可用时，尝试从广播获取（部分三星设备需要）
        Intent intent = lastBatteryStatus;
        if (intent != null && intent.hasExtra("current_now")) {
            // 三星和部分厂商在广播中提供 current_now，单位通常是 µA
            return intent.getIntExtra("current_now", Integer.MIN_VALUE);
        }
        
        return Integer.MIN_VALUE;
    }

    /**
     * 获取平均电池电流，单位 µA。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getCurrentAverage() {
        BatteryManager bm = batteryManager;
        if (bm == null) return Integer.MIN_VALUE;
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
    }

    /**
     * 获取剩余电量，单位 µAh。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getChargeCounter() {
        BatteryManager bm = batteryManager;
        if (bm == null) return Integer.MIN_VALUE;
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    }

    /**
     * 获取电池剩余容量百分比 (0-100)。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getCapacity() {
        BatteryManager bm = batteryManager;
        if (bm == null) return Integer.MIN_VALUE;
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /**
     * 获取电池能量计数器，单位 nWh（纳瓦时）。
     * 返回 Long.MIN_VALUE 表示不可用。
     */
    public static long getEnergyCounter() {
        BatteryManager bm = batteryManager;
        if (bm == null) return Long.MIN_VALUE;
        return bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
    }

    /**
     * 获取电池电压，单位 mV（毫伏）。
     * 从 ACTION_BATTERY_CHANGED 广播获取。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getVoltage() {
        Intent intent = lastBatteryStatus;
        if (intent == null) return Integer.MIN_VALUE;
        return intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Integer.MIN_VALUE);
    }

    /**
     * 获取电池温度，单位 0.1°C（需除以10得到摄氏度）。
     * 从 ACTION_BATTERY_CHANGED 广播获取。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getTemperature() {
        Intent intent = lastBatteryStatus;
        if (intent == null) return Integer.MIN_VALUE;
        return intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
    }

    /**
     * 获取电池健康状态。
     * 返回值为 BatteryManager.BATTERY_HEALTH_* 常量之一。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getHealth() {
        Intent intent = lastBatteryStatus;
        if (intent == null) return Integer.MIN_VALUE;
        return intent.getIntExtra(BatteryManager.EXTRA_HEALTH, Integer.MIN_VALUE);
    }

    /**
     * 获取充电状态。
     * 返回值为 BatteryManager.BATTERY_STATUS_* 常量之一。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getStatus() {
        Intent intent = lastBatteryStatus;
        if (intent == null) return Integer.MIN_VALUE;
        return intent.getIntExtra(BatteryManager.EXTRA_STATUS, Integer.MIN_VALUE);
    }
}
