package io.github.lumkit.tweak.common.database.battery.table

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 放电会话中一段连续前台应用占用。
 */
@Serializable
@Entity(
    tableName = "battery_app_usage",
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "packageName"]),
    ],
)
data class BatteryAppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)
