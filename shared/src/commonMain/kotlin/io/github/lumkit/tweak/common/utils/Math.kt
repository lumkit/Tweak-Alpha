package io.github.lumkit.tweak.common.utils

import kotlin.math.abs
import kotlin.math.round

/**
 * 格式化电压，单位：mV、V、kV
 */
fun Int.formatVoltage(): String {
    val absValue = abs(toDouble())

    val (value, unit) = when {
        absValue < 1_000 -> toDouble() to "mV"
        absValue < 1_000_000 -> toDouble() / 1_000 to "V"
        else -> toDouble() / 1_000_000 to "kV"
    }

    return "${value.formatNumber()}$unit"
}

/**
 * 格式化功率，单位：mW、W、kW
 */
fun Int.formatPower(): String {
    val absValue = abs(toDouble())

    val (value, unit) = when {
        absValue < 1_000 -> toDouble() to "mW"
        absValue < 1_000_000 -> toDouble() / 1_000 to "W"
        else -> toDouble() / 1_000_000 to "kW"
    }

    return "${value.formatNumber()}$unit"
}

/**
 * 格式化电流，单位：mA、A、kA
 */
fun Int.formatCurrent(): String {
    val absValue = abs(toDouble())

    val (value, unit) = when {
        absValue < 1_000 -> toDouble() to "mA"
        absValue < 1_000_000 -> toDouble() / 1_000 to "A"
        else -> toDouble() / 1_000_000 to "kA"
    }

    return "${value.formatNumber()}$unit"
}

private fun Double.formatNumber(): String {
    // 保留两位小数
    val rounded = round(this * 100) / 100

    // 去掉尾随 0 和 .
    return rounded.toString()
        .removeSuffix(".0")
        .removeSuffix(".00")
        .let {
            if ('.' in it) {
                it.trimEnd('0').trimEnd('.')
            } else {
                it
            }
        }
}