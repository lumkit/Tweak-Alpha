package io.github.lumkit.tweak.common.database.battery

/**
 * 电池记录会话的充放电状态。
 */
enum class BatteryChargeState(val code: Int) {
    /** 放电 */
    DISCHARGING(0),

    /** 充电（需连续保持 [BatteryRecordDefaults.CONFIRM_MS] 后 confirmed=true 才视为有效） */
    CHARGING(1),

    /**
     * 充满且仍插电（涓流/满电维持）。
     * 独立会话录入；充电历史列表/图表仍只认 [CHARGING]。
     */
    FULL(2);

    /** 是否为「充电类」历史（仅 CHARGING；FULL 不算） */
    val isChargingHistory: Boolean
        get() = this == CHARGING

    /** 创建会话时是否默认已确认（充电需确认窗口，其余立即确认） */
    val defaultConfirmed: Boolean
        get() = this != CHARGING

    companion object {
        fun fromCode(code: Int): BatteryChargeState =
            entries.firstOrNull { it.code == code } ?: DISCHARGING
    }
}

object BatteryRecordDefaults {
    /** 充电状态确认窗口（毫秒） */
    const val CONFIRM_MS = 5_000

    /** 默认采样间隔档位（与面板刷新一致：档位 × 500ms） */
    const val DEFAULT_INTERVAL_LEVEL = 2

    const val INTERVAL_LEVEL_RANGE_MS = 500
}
