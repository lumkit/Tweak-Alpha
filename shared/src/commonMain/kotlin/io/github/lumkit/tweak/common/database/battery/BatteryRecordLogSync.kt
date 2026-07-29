package io.github.lumkit.tweak.common.database.battery

/**
 * 电池日志 → Room 同步入口（C2 事件分流）。
 */
expect object BatteryRecordLogSync {
    /** 兼容旧调用，等价 [syncOnStartup] */
    suspend fun syncAll()

    /** 兼容旧调用，等价 [syncOnStartup] */
    suspend fun syncIncremental()

    /** 启动后台：有预算的目录增量同步 */
    suspend fun syncOnStartup()

    /** 单文件尾部增量（MODIFY / CLOSE_WRITE） */
    suspend fun syncOnLogAppended(path: String)

    /** 新文件：读头建 session 后增量 */
    suspend fun syncOnLogCreated(path: String)

    /** 删除：清 offset，不删 Room */
    suspend fun syncOnLogRemoved(path: String)
}
