package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.daemon.A11yWatchDaemonConfig
import io.github.lumkit.tweak.common.daemon.TweakDaemon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 特权（Root / Shizuku）校验通过后的统一初始化入口。
 * 后续依赖特权的初始化逻辑都放在这里。
 *
 * 使用 [NonCancellable]：Splash 跳转 Main 时 composition / LaunchedEffect 会被取消，
 * 不能让 daemon 安装启动跟着被掐断。
 */
object PrivilegedAppInitializer {

    private const val TAG = "PrivilegedAppInitializer"

    private val mutex = Mutex()
    private var appsReady = false
    private var daemonReady = false

    suspend fun onPrivilegeReady() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (!appsReady) {
                AppsHelper.init()
                appsReady = true
            }
            if (!daemonReady) {
                daemonReady = bootstrapDaemon()
            } else {
                logD("daemon already ready, skip", TAG)
            }
        }
    }

    /** @return true 表示 tweakd 已成功启动（或已在跑） */
    private suspend fun bootstrapDaemon(): Boolean {
        return try {
            A11yWatchDaemonConfig.syncFromDataStore()
            // 旧版 tweakd：无 a11y_watch / idle_poll / tcp 字段时，重启一次以升级
            val status = TweakDaemon.status()
            val needsUpgrade =
                status == null ||
                    !status.raw.contains("a11y_watch=1") ||
                    !status.raw.contains("idle_poll=1") ||
                    !status.raw.contains("tcp=")
            if (needsUpgrade && TweakDaemon.isRunning()) {
                TweakDaemon.stop()
            }
            val started = TweakDaemon.start()
            logD(
                "TweakDaemon.start => $started needsUpgrade=$needsUpgrade",
                TAG,
            )
            started
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("daemon bootstrap failed: ${e.message}", e, TAG)
            false
        }
    }
}
