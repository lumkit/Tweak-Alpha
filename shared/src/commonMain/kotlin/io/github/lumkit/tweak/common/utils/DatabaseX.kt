package io.github.lumkit.tweak.common.utils

import androidx.room.RoomDatabase

/**
 * 数据库构建器
 */
expect inline fun <reified DB: RoomDatabase> getDatabaseBuilder(dbName: String): RoomDatabase.Builder<DB>