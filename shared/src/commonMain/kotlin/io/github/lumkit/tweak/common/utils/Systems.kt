package io.github.lumkit.tweak.common.utils

import androidx.activity.compose.ManagedActivityResultLauncher

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

/**
 * 获取应用是否有通知权限
 */
expect fun hasNotificationPermission(): Boolean

/**
 * 申请通知权限
 */
expect fun ManagedActivityResultLauncher<String, Boolean>.requestNotificationPermission()

/**
 * 通知权限是否被关闭
 */
expect fun areNotificationsEnabled(): Boolean

/**
 * 跳转至App详情
 */
expect fun jumpToAppInfo()