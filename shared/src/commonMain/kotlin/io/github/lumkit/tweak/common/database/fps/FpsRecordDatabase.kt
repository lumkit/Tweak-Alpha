package io.github.lumkit.tweak.common.database.fps

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import io.github.lumkit.tweak.common.database.fps.dao.FpsRecordDao
import io.github.lumkit.tweak.common.database.fps.table.FpsMetricEntity
import io.github.lumkit.tweak.common.database.fps.table.FpsNoteSessionEntity

@Database(
    entities = [
        FpsNoteSessionEntity::class,
        FpsMetricEntity::class,
    ],
    version = 1
)
@ConstructedBy(FpsRecordDatabaseConstructor::class)
abstract class FpsRecordDatabase: RoomDatabase() {
    abstract fun recordDao(): FpsRecordDao
}

expect object FpsRecordDatabaseConstructor : RoomDatabaseConstructor<FpsRecordDatabase> {
    override fun initialize(): FpsRecordDatabase
}