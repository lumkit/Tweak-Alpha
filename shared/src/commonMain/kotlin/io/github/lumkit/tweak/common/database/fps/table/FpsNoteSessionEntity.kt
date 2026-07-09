package io.github.lumkit.tweak.common.database.fps.table

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * FPS 记录会话表（一次完整采样任务）
 *
 * 该表用于记录一次 FPS 性能分析的“会话级元信息”，不包含具体采样数据。
 * 每个 Session 对应一次应用性能监控过程（启动 → 录制 → 结束）。
 *
 * 典型用途：
 * - FPS 监控记录列表展示
 * - 进入详情页加载对应采样数据
 * - 关联设备信息与运行环境
 */
@Serializable
@Entity(tableName = "fps_note_session")
data class FpsNoteSessionEntity(

    /** 主键ID */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 被监控应用包名 */
    val packageName: String,

    /** 应用名称（用于列表展示） */
    val appName: String,

    /** 应用图标路径（本地缓存路径，可为空） */
    val iconPath: String? = null,

    /** App版本信息 **/
    val appVersion: String,

    /** 本次录制的时间戳 */
    val recordingTime: Long,

    /** 设备型号（如 Pixel 8 / Xiaomi 14） */
    val deviceModel: String,

    /** SoC 信息 JSON（例如 CPU/GPU/芯片型号等结构化信息） */
    val socJson: String? = null,

    /** 平台类型名称（如手机、平板） */
    val platformName: String,

    /** 版本名称（如Android 16） **/
    val platformVersionName: String,

    /** 设备屏幕宽度 **/
    val deviceScreenWidth: Int,

    /** 设备屏幕高度 **/
    val deviceScreenHeight: Int,

    /** 用户备注信息（用于标记一次测试环境或问题描述） */
    val note: String? = null,

    /** 是否已结束录制 */
    val isRecordEnd: Boolean = false,

    /** 删除标记（软删除，true 表示已删除） */
    val delete: Boolean = false,
)
