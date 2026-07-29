package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.database.battery.BatteryRecordLogSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private var toolkitReady = false

    /** 电池日志同步不阻塞启动；独立 Supervisor 避免被 Splash 取消 */
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun onPrivilegeReady() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (!appsReady) {
                AppsHelper.init()
                appsReady = true
            }
            if (!toolkitReady) {
                toolkitReady = bootstrapToolkit()
            }
            if (!daemonReady) {
                daemonReady = bootstrapDaemon()
            } else {
                logD("daemon already ready, skip", TAG)
            }
            scheduleBatteryLogSync()
        }
    }

    private fun scheduleBatteryLogSync() {
        bgScope.launch {
            runCatching {
                // 增量 + 字节预算，禁止启动路径 syncAll 全量重扫
                BatteryRecordLogSync.syncOnStartup()
            }.onFailure {
                logE("battery log sync failed: ${it.message}", it, TAG)
            }
        }
    }

    private suspend fun bootstrapToolkit(): Boolean {
        return try {
            val toybox = ToolkitInstaller.ensureToybox()
            val busybox = ToolkitInstaller.ensureBusybox()
            // 进程列表命令缓存依赖 toybox 路径，安装后强制重探
            ProcessUtils.reset()
            ProcessUtilLite.reset()
            logD("toolkit ready toybox=${toybox.isNotBlank()} busybox=$busybox", TAG)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("toolkit bootstrap failed: ${e.message}", e, TAG)
            false
        }
    }

    /**
     * @return true 表示已处理（含用户未启用时的跳过，或启动成功/已在跑）
     */
    private suspend fun bootstrapDaemon(): Boolean {
        return try {
            val started = NativeDaemonController.ensureRunningIfEnabled()
            logD("NativeDaemonController.ensureRunningIfEnabled => $started", TAG)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("daemon bootstrap failed: ${e.message}", e, TAG)
            false
        }
    }
}
