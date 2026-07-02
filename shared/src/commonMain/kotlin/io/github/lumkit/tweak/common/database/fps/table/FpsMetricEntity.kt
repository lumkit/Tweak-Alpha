package io.github.lumkit.tweak.common.database.fps.table

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 性能采样数据表（每秒/每帧记录一次）
 *
 * 用于记录一次 Session 中的实时性能指标，包括 FPS、CPU、GPU、温度、电量等信息。
 * 用于后续进行性能分析、趋势绘制与卡顿定位。
 */
@Entity(
    tableName = "fps_metric",
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class FpsMetricEntity(

    /** 主键ID */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属 Session ID */
    val sessionId: Long,

    /** 采样时间戳（ms） */
    val timestamp: Long,

    /** FPS（帧率） */
    val fps: Double,

    /** 电池温度（℃） */
    val batteryTemperature: Double,

    /** 电池电量（%） */
    val batteryLevel: Double,

    /** 电池电流（μA 或 mA） */
    val batteryCurrent: Int,

    /** CPU 使用率（%） */
    val cpuLoad: Double,

    /** CPU 当前频率（Hz） */
    val cpuCurrentFreq: Long,

    /** CPU cycles（需 perf_event / simpleperf） */
    val cpuCurrentCycles: Long,

    /** GPU 使用率（%） */
    val gpuLoad: Double? = null,

    /** GPU 当前频率（Hz） */
    val gpuCurrentFreq: Long? = null,

    /** 单帧耗时（ms） */
    val frameTime: Int,

    /** 内存相关指标（自定义） */
    val memoryFreq: Long,

    /** CPU / SoC 温度（℃） */
    val coreTemperature: Double,

    /** 采样线程标识 */
    val thread: String? = null,

    /** 相对 Session 开始时间（ms） */
    val recordingTime: Long,
)