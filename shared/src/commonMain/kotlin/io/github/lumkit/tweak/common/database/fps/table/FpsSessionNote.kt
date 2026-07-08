package io.github.lumkit.tweak.common.database.fps.table

import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable

@Serializable
data class FpsSessionNote(
    @ColumnInfo(name = "sessionId")
    val sessionId: Long,
    @ColumnInfo(name = "appName")
    val appName: String,
    @ColumnInfo(name = "appPackageName")
    val appPackageName: String,
    @ColumnInfo(name = "appIconPath")
    val appIconPath: String?,
    @ColumnInfo(name = "createTime")
    val createTime: Long,
    @ColumnInfo(name = "avgFps")
    val avgFps: Float,
    @ColumnInfo(name = "avgPower")
    val avgPower: Int,
    @ColumnInfo(name = "recordingDuration")
    val recordingDuration: Long,
)
