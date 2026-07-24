package io.github.lumkit.tweak.common.database.battery.table

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import kotlinx.serialization.Serializable

/**
 * 电池记录会话表。
 *
 * 充/放电状态切换时新建 session；充电需连续保持 [BatteryRecordDefaults.CONFIRM_MS]
 * 后将 [confirmed] 置为 true，未确认的充电会话可逻辑删除。
 */
@Serializable
@Entity(
    tableName = "battery_record_session",
    indices = [
        Index(value = ["deleted", "startedAt"]),
        Index(value = ["state", "startedAt"]),
    ],
)
data class BatteryRecordSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 会话开始时间（epoch ms） */
    val startedAt: Long,
    /** 会话结束时间（epoch ms）；进行中为 null */
    val endedAt: Long? = null,
    /** 采样间隔（毫秒），创建时固化，不受后续设置变更影响 */
    val intervalMs: Int,
    /**
     * 充放电状态码，见 [BatteryChargeState.code]
     */
    val state: Int,
    /** 是否已通过确认窗口（充电连续 5s） */
    val confirmed: Boolean = false,
    /** 确认窗口时长（毫秒），默认 5000 */
    val confirmMs: Int = BatteryRecordDefaults.CONFIRM_MS,
    /** 逻辑删除 */
    val deleted: Boolean = false,
    /** 行创建时间（epoch ms） */
    val createdAt: Long = startedAt,
) {
    val chargeState: BatteryChargeState
        get() = BatteryChargeState.fromCode(state)
}
