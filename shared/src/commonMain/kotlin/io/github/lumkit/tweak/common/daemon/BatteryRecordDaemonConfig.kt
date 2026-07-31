package io.github.lumkit.tweak.common.daemon

/**
 * 电池记录采样配置，写入 daemon 目录的 `battery_record.conf` 供 TweakServer 读取。
 */
expect object BatteryRecordDaemonConfig {
    suspend fun write(
        intervalMs: Int,
        maxPartBytes: Long = DaemonPaths.DEFAULT_BATTERY_LOG_MAX_PART_BYTES,
        enabled: Boolean = true,
        dualCell: Boolean = false,
        currentScale: Long = -1_000L,
    )

    suspend fun syncFromDataStore()
}
