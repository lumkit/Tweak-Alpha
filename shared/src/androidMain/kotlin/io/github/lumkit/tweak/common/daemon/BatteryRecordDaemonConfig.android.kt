package io.github.lumkit.tweak.common.daemon

/**
 * 空实现：tweakd 不再读取 battery_record.conf。
 */
actual object BatteryRecordDaemonConfig {
    actual suspend fun writeIntervalMs(intervalMs: Int) = Unit
    actual suspend fun syncFromDataStore() = Unit
}
