package io.github.lumkit.tweak.common.database.battery

actual object BatteryRecordLogSync {
    actual suspend fun syncAll() = BatteryRecordLogImporter.syncOnStartup()
    actual suspend fun syncIncremental() = BatteryRecordLogImporter.syncOnStartup()
    actual suspend fun syncOnStartup() = BatteryRecordLogImporter.syncOnStartup()
    actual suspend fun syncOnLogAppended(path: String) = BatteryRecordLogImporter.syncOnLogAppended(path)
    actual suspend fun syncOnLogCreated(path: String) = BatteryRecordLogImporter.syncOnLogCreated(path)
    actual suspend fun syncOnLogRemoved(path: String) = BatteryRecordLogImporter.syncOnLogRemoved(path)
}
