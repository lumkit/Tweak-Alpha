package io.github.lumkit.tweak.common.utils

/**
 * 无障碍服务启停统一入口（特权 Shell）。
 * 设置页开关、开机广播、Splash 特权初始化均应走这里，禁止散写 settings put。
 */
expect object AccessibilityBootstrap {
    /** 组件名：`pkg/serviceClass` */
    val serviceComponent: String

    /** settings 列表是否包含本服务 */
    suspend fun isAccessibilityServiceEnabled(): Boolean

    /** 服务进程是否已 connected（比 settings 更能反映“存活”） */
    suspend fun isAccessibilityServiceConnected(): Boolean

    /** 写入 settings 启用本服务（幂等） */
    suspend fun startAccessibilityService(): Boolean

    /** 从 settings 移除本服务 */
    suspend fun stopAccessibilityService(): Boolean

    /**
     * 特权可用时启用。
     * @param requireUserPreference 为 true 时仅当用户开启了「无障碍服务」选项才启动
     */
    suspend fun enableIfPrivileged(requireUserPreference: Boolean = false): Boolean

    /**
     * 用户已开启选项且服务未存活时拉起；已存活则跳过。
     * 用于开机广播 / 特权就绪。
     */
    suspend fun ensureRunningIfUserEnabled(): Boolean
}

/** @see AccessibilityBootstrap.enableIfPrivileged */
expect suspend fun ensureAccessibilityServiceEnabled(): Boolean
