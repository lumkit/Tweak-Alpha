package io.github.lumkit.tweak.common.daemon

/**
 * 将无障碍保活巡检配置写入 daemon 工作区的 `a11y_watch.conf`，
 * 供 TweakServer 读取（enabled / interval_ms / component）。
 */
expect object A11yWatchDaemonConfig {
    suspend fun write(
        intervalMs: Int,
        enabled: Boolean = true,
    )

    suspend fun syncFromDataStore()
}
