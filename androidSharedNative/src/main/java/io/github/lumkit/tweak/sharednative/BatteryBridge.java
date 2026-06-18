package io.github.lumkit.tweak.sharednative;

import android.content.Context;
import android.os.BatteryManager;

/**
 * 电池信息桥接类，通过 Android BatteryManager API 获取标准化电池数据。
 * BatteryManager 已对各厂商数据做了归一化处理，比直接读 sysfs 更可靠。
 */
public final class BatteryBridge {

    private BatteryBridge() {
    }

    private static volatile BatteryManager batteryManager;

    /**
     * 初始化 BatteryManager 实例。需要在 Application 初始化时调用。
     */
    public static void init(Context context) {
        if (batteryManager == null) {
            batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        }
    }

    /**
     * 获取当前电池电流，单位 µA。
     * 正值表示充电电流，负值表示放电电流。
     * 返回 Integer.MIN_VALUE 表示不可用。
     */
    public static int getCurrentNow() {
        BatteryManager bm = batteryManager;
        if (bm == null) return Integer.MIN_VALUE;
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
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
}
