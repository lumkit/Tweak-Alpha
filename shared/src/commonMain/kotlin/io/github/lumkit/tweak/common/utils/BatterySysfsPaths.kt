package io.github.lumkit.tweak.common.utils

/** sysfs / uevent 路径（与 [BatteryUtils] / tweak_server 采样共用）。 */
object BatterySysfsPaths {
    val ueventPaths = listOf(
        "/sys/class/power_supply/bms/uevent",
        "/sys/class/power_supply/battery/uevent",
        "/sys/class/power_supply/Battery/uevent",
    )

    val voltagePaths = listOf(
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/Battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
    )

    /** 瞬时电流：仅 `current_now`（BatteryRecorder / 内核规范）。 */
    val currentNowPaths = listOf(
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/Battery/current_now",
    )

    /** 仅当 [currentNowPaths] 不可读时的后备。 */
    val currentAvgPaths = listOf(
        "/sys/class/power_supply/bms/current_avg",
        "/sys/class/power_supply/battery/current_avg",
        "/sys/class/power_supply/Battery/current_avg",
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

    val capacityPaths = listOf(
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/Battery/capacity",
        "/sys/class/power_supply/bms/capacity",
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
}
