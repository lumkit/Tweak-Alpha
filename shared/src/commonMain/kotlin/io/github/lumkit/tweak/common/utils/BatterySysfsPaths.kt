package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.utils.BatterySysfsPaths.currentNowPaths


/**
 * sysfs 路径（对齐 BatteryRecorder `power_reader.cpp`：固定 `battery/` 节点）。
 * 部分 OEM 用 `Battery` 大小写或仅暴露 `bms`，作为次选。
 */
object BatterySysfsPaths {
    /** BR JNI 仅打开该路径；失败再试同族路径。 */
    val currentNowPaths = listOf(
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/Battery/current_now",
        "/sys/class/power_supply/bms/current_now",
    )

    val voltageNowPaths = listOf(
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/Battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
    )

    val capacityPaths = listOf(
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/Battery/capacity",
        "/sys/class/power_supply/bms/capacity",
    )

    val statusPaths = listOf(
        "/sys/class/power_supply/battery/status",
        "/sys/class/power_supply/Battery/status",
        "/sys/class/power_supply/bms/status",
    )

    val temperaturePaths = listOf(
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/Battery/temp",
        "/sys/class/power_supply/bms/temp",
    )

    val cycleCountPaths = listOf(
        "/sys/class/power_supply/battery/cycle_count",
        "/sys/class/power_supply/Battery/cycle_count",
        "/sys/class/power_supply/bms/cycle_count",
    )

    val designCapacityPaths = listOf(
        "/sys/class/power_supply/battery/charge_full_design",
        "/sys/class/power_supply/Battery/charge_full_design",
        "/sys/class/power_supply/bms/charge_full_design",
    )

    val currentCapacityPaths = listOf(
        "/sys/class/power_supply/battery/charge_full",
        "/sys/class/power_supply/Battery/charge_full",
        "/sys/class/power_supply/bms/charge_full",
    )

    val ueventPaths = listOf(
        "/sys/class/power_supply/battery/uevent",
        "/sys/class/power_supply/Battery/uevent",
        "/sys/class/power_supply/bms/uevent",
    )

    /** @deprecated 兼容旧引用；瞬时电流请用 [currentNowPaths] */
    val voltagePaths get() = voltageNowPaths
}
