package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.common.daemon.TweakDaemon.install
import io.github.lumkit.tweak.common.daemon.TweakDaemon.stop


/**
 * 特权常驻进程控制面（C2：`libtweak_starter.so` → `app_process` TweakServer）。
 *
 * 安装校验 starter 是否在 [android.content.pm.ApplicationInfo.nativeLibraryDir]；
 * 启停与健康检查走 Binder（[io.github.lumkit.tweak.server.ipc.TweakServerConnection]）。
 */
expect object TweakDaemon {
    /** 确保工作目录可写，并确认 starter 存在 */
    suspend fun install(): Boolean

    /**
     * 工作区 starter / server.apk 是否与当前 App 包内产物不一致。
     * 为 true 时应先 [stop] 再 [install]，否则已在跑的 app_process 仍会用旧 dex。
     */
    suspend fun artifactsOutdated(): Boolean

    /** 后台启动 TweakServer；已能 ping 则视为成功 */
    suspend fun start(): Boolean

    /** Binder stop；失败时按 server pidfile kill */
    suspend fun stop(): Boolean

    suspend fun isRunning(): Boolean

    suspend fun ping(): Boolean

    suspend fun status(): TweakDaemonStatus?

    suspend fun version(): String?

    /** 通知已运行的 Server 重读 conf；未连接时 no-op */
    suspend fun reloadConfig()
}
