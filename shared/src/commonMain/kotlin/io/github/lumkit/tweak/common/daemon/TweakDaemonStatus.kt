package io.github.lumkit.tweak.common.daemon

/**
 * 特权常驻 TweakServer 状态快照。
 */
data class TweakDaemonStatus(
    val running: Boolean,
    val pid: Int = -1,
    val version: String = "",
    val sock: String = "",
    val raw: String = "",
)
