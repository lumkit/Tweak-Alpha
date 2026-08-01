package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.database.battery.BatteryRecordLogSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * 特权（Root / Shizuku）校验通过后的统一初始化入口。
 * 后续依赖特权的初始化逻辑都放在这里。
 *
 * Splash 只等待轻量步骤（AppsHelper.init）；Daemon / toolkit / 无障碍启用
 * 在独立 [bgScope] + [NonCancellable] 中执行，避免 shell 卡住或重装 server.apk
 * 时阻塞进首页。跳转 Main 后 composition 取消也不会掐断后台安装。
 */
object PrivilegedAppInitializer {

    private const val TAG = "PrivilegedAppInitializer"

    /** Daemon / toolkit 安装在极端机型上可能很慢；超时后交由无障碍 connected 等路径重试 */
    private val heavyBootstrapTimeout = 90.seconds

    private val mutex = Mutex()
    private var appsReady = false
    private var heavyBootstrapScheduled = false

    /** 电池日志同步与 heavy bootstrap 均不阻塞启动 */
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 尽快返回：仅保证 AppsHelper 已注册；其余在后台跑。
     */
    suspend fun onPrivilegeReady() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (!appsReady) {
                AppsHelper.init()
                appsReady = true
            }
            if (!heavyBootstrapScheduled) {
                heavyBootstrapScheduled = true
                bgScope.launch(NonCancellable) {
                    runHeavyBootstrap()
                }
            }
            scheduleBatteryLogSync()
        }
    }

    private suspend fun runHeavyBootstrap() {
        try {
            withTimeout(heavyBootstrapTimeout) {
                coroutineScope {
                    val toolkitJob = async { bootstrapToolkit() }
                    val daemonJob = async { bootstrapDaemon() }
                    val a11yJob = async { bootstrapAccessibility() }
                    toolkitJob.await()
                    daemonJob.await()
                    a11yJob.await()
                }
            }
        } catch (e: TimeoutCancellationException) {
            logE("privilege heavy bootstrap timeout after $heavyBootstrapTimeout", e, TAG)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("privilege heavy bootstrap failed: ${e.message}", e, TAG)
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
     *
     * 产物过期时由 [NativeDaemonController.ensureRunning] 停旧进程并重装 starter/server.apk。
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

    private suspend fun bootstrapAccessibility(): Boolean {
        return try {
            val ok = AccessibilityBootstrap.ensureRunningIfUserEnabled()
            logD("AccessibilityBootstrap.ensureRunningIfUserEnabled => $ok", TAG)
            ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("accessibility bootstrap failed: ${e.message}", e, TAG)
            false
        }
    }
}
