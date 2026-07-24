package io.github.lumkit.tweak.common.database.battery

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import io.github.lumkit.tweak.common.database.battery.dao.BatteryRecordDao
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity

@Database(
    entities = [
        BatteryRecordSessionEntity::class,
        BatteryRecordSampleEntity::class,
    ],
    version = 1,
)
@ConstructedBy(BatteryRecordDatabaseConstructor::class)
abstract class BatteryRecordDatabase : RoomDatabase() {
    abstract fun recordDao(): BatteryRecordDao
}

expect object BatteryRecordDatabaseConstructor : RoomDatabaseConstructor<BatteryRecordDatabase> {
    override fun initialize(): BatteryRecordDatabase
}
