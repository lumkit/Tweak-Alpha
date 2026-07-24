package io.github.lumkit.tweak.common.database.battery.table

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 电池采样点。跟随 session，不做单独逻辑删除。
 */
@Serializable
@Entity(
    tableName = "battery_record_sample",
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "timestamp"]),
    ],
)
data class BatteryRecordSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属会话 ID */
    val sessionId: Long,
    /** 采样时间戳（epoch ms） */
    val timestamp: Long,
    /** 电量 0–100 */
    val level: Int,
    /** 电压 mV，读不到则为 null */
    val voltageMv: Int? = null,
    /** 温度 ℃，读不到则为 null */
    val temperatureC: Float? = null,
    /** 亮屏：true=亮屏，false=息屏 */
    val screenOn: Boolean,
    /** 电流 mA；约定正值充电、负值放电；读不到则为 null */
    val currentMa: Int? = null,
)
