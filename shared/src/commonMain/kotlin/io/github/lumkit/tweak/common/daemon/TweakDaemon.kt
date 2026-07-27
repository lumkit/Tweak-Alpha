package io.github.lumkit.tweak.common.daemon

/**
 * 特权环境下的独立 native daemon 控制面。
 *
 * 二进制以 `assets/tweakd/<abi>/tweakd` 打进 APK，安装到 [DaemonPaths] 解析的目录
 * （Root→`/data/adb/...`，Shizuku→tmp）。启动时若已安装且 SHA-256 与 assets 一致则跳过拷贝。
 * 通过 127.0.0.1 TCP（端口写入 port 文件）与 App 通信；业务为无障碍保活巡检。
 */
expect object TweakDaemon {
    /** 确保特权目录中有完好的 tweakd：已存在且哈希匹配则跳过拷贝 */
    suspend fun install(): Boolean

    /** 后台启动；已运行则视为成功 */
    suspend fun start(): Boolean

    /** 发送 STOP；失败时尝试按 pidfile kill */
    suspend fun stop(): Boolean

    suspend fun isRunning(): Boolean

    suspend fun ping(): Boolean

    suspend fun status(): TweakDaemonStatus?

    suspend fun version(): String?
}
