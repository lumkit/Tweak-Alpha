package io.github.lumkit.tweak.common.utils

expect fun isDebugBuild(): Boolean

expect fun restartApp()

expect val SDK_INT: Int

expect val BOARD: String

/**
* 获取应用是否忽略电池优化
 */
expect fun isIgnoringBatteryOptimizations(): Boolean

/**
 * 尝试设置应用忽略电池优化
 */
expect fun trySetIsIgnoringBatteryOptimizations()
