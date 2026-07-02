package io.github.lumkit.tweak.common.utils

import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.lumkit.tweak.application

actual inline fun <reified DB : RoomDatabase> getDatabaseBuilder(dbName: String): RoomDatabase.Builder<DB> {
    val dbFile = application.getDatabasePath(dbName)
    return Room.databaseBuilder<DB>(
        context = application,
        name = dbFile.absolutePath
    )
}