package io.github.lumkit.tweak.common.database.battery

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.lumkit.tweak.common.database.battery.dao.BatteryRecordDao
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryUidPowerEntity

@Database(
    entities = [
        BatteryRecordSessionEntity::class,
        BatteryRecordSampleEntity::class,
        BatteryAppUsageEntity::class,
        BatteryAppUsageSampleEntity::class,
        BatteryUidPowerEntity::class,
    ],
    version = 3,
)
@ConstructedBy(BatteryRecordDatabaseConstructor::class)
abstract class BatteryRecordDatabase : RoomDatabase() {
    abstract fun recordDao(): BatteryRecordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `battery_app_usage` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_battery_app_usage_sessionId` ON `battery_app_usage` (`sessionId`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_battery_app_usage_sessionId_packageName` ON `battery_app_usage` (`sessionId`, `packageName`)",
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `battery_app_usage_sample` (
                        `usageId` INTEGER NOT NULL,
                        `sampleId` INTEGER NOT NULL,
                        PRIMARY KEY(`usageId`, `sampleId`)
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_battery_app_usage_sample_sampleId` ON `battery_app_usage_sample` (`sampleId`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_battery_app_usage_sample_usageId` ON `battery_app_usage_sample` (`usageId`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `battery_uid_power` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `uid` INTEGER NOT NULL,
                        `packageName` TEXT,
                        `deltaMah` REAL NOT NULL,
                        `fgMah` REAL,
                        `bgMah` REAL,
                        `fgsMah` REAL,
                        `capturedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_battery_uid_power_sessionId_uid` ON `battery_uid_power` (`sessionId`, `uid`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_battery_uid_power_sessionId` ON `battery_uid_power` (`sessionId`)",
                )
            }
        }
    }
}

expect object BatteryRecordDatabaseConstructor : RoomDatabaseConstructor<BatteryRecordDatabase> {
    override fun initialize(): BatteryRecordDatabase
}
