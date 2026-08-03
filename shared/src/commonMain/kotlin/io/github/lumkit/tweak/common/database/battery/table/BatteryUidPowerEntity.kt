package io.github.lumkit.tweak.common.database.battery.table

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 放电会话内 batterystats UID 估算功耗差分。
 */
@Serializable
@Entity(
    tableName = "battery_uid_power",
    indices = [
        Index(value = ["sessionId", "uid"], unique = true),
        Index("sessionId"),
    ],
)
data class BatteryUidPowerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val uid: Int,
    val packageName: String? = null,
    val deltaMah: Double,
    val fgMah: Double? = null,
    val bgMah: Double? = null,
    val fgsMah: Double? = null,
    val capturedAt: Long,
    val updatedAt: Long,
)
