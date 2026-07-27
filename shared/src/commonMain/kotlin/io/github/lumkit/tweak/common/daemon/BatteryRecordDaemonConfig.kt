package io.github.lumkit.tweak.common.daemon

/**
 * 电池采样已与 native daemon 解耦；保留 API 为空操作，避免旧调用编译失败。
 */
expect object BatteryRecordDaemonConfig {
    suspend fun writeIntervalMs(intervalMs: Int)
    suspend fun syncFromDataStore()
}
