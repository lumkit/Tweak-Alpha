package io.github.lumkit.tweak.common.utils

import androidx.compose.runtime.Composable

/**
 * 检查是否拥有悬浮窗权限
 */
expect fun canDrawOverlays(): Boolean

/**
 * 记住悬浮窗权限申请启动器。
 *
 * 调用返回的 lambda 时：
 * - 已有权限 → 直接执行 [onGranted]
 * - 无权限 → 跳转系统悬浮窗设置页，用户返回后自动检查，有权限则执行 [onGranted]
 *
 * @param onGranted 权限授予后的回调
 * @return 触发权限检查/申请的 lambda
 */
@Composable
expect fun rememberRequestOverlayPermission(onGranted: () -> Unit): () -> Unit
