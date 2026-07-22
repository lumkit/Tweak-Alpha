package io.github.lumkit.tweak.common.daemon

/**
 * 特权环境下的独立 native daemon 控制面。
 *
 * 二进制以 `libtweakd.so` 打进 APK，安装时拷到 [io.github.lumkit.tweak.common.ConstCommon.Path.DAEMON_BIN]，
 * 通过 Unix Domain Socket 与 App 通信（socket 权限 0666，App UID 可直连）。
 */
expect object TweakDaemon {
    /** 从 APK nativeLibraryDir 安装/更新 ELF 到特权目录 */
    suspend fun install(): Boolean

    /** setsid 后台启动；已运行则视为成功 */
    suspend fun start(): Boolean

    /** 发送 STOP；失败时尝试按 pidfile kill */
    suspend fun stop(): Boolean

    suspend fun isRunning(): Boolean

    suspend fun ping(): Boolean

    suspend fun status(): TweakDaemonStatus?

    suspend fun version(): String?
}
