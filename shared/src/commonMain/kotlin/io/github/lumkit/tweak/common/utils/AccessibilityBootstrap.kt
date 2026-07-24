package io.github.lumkit.tweak.common.utils

/**
 * 在特权校验通过后，尝试启用无障碍服务（由系统绑定）。
 * @return 是否已成功发起启用（特权不足时返回 false）
 */
expect suspend fun ensureAccessibilityServiceEnabled(): Boolean
