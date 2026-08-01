package io.github.lumkit.tweak.common.database.battery.table

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * 应用耗电段与电池采样点的多对多关联。
 */
@Serializable
@Entity(
    tableName = "battery_app_usage_sample",
    primaryKeys = ["usageId", "sampleId"],
    indices = [
        Index("sampleId"),
        Index("usageId"),
    ],
)
data class BatteryAppUsageSampleEntity(
    val usageId: Long,
    val sampleId: Long,
)
