package io.github.lumkit.tweak.service

import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.common.feature.startUpdateEngineService
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * 无障碍 Daemon：在 AAS 存活期间周期性巡检并拉起依赖服务。
 *
 * 由 [TweakAccessibilityService] 在 `onServiceConnected` 启动、`onDestroy` 停止。
 * 使用无障碍 Context 启动 Service，规避普通后台启动限制。
 */
internal class AccessibilityDaemon(
    private val context: Context,
    private val ensureOverlay: () -> Unit,
) {
    companion object {
        private const val TAG = "AccessibilityDaemon"
        private val TICK_INTERVAL = 5.seconds
    }

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)
    private var loopJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (loopJob?.isActive == true) return
        isRunning = true
        loopJob = scope.launch {
            logD("daemon loop started", TAG)
            // 立即执行一次，再进入周期巡检
            while (isActive) {
                runCatching { tick() }
                    .onFailure { logD("daemon tick failed: ${it.message}", TAG) }
                delay(TICK_INTERVAL)
            }
        }
    }

    fun stop() {
        isRunning = false
        loopJob?.cancel()
        loopJob = null
        supervisor.cancel()
        logD("daemon loop stopped", TAG)
    }

    private suspend fun tick() {
        ensureOverlay()
        ensureKeepAliveService()
        ensureUpdateEngineService()
    }

    private fun ensureKeepAliveService() {
        runCatching {
            context.startService(Intent(context, KeepAliveService::class.java))
        }.onFailure {
            logD("ensure KeepAlive failed: ${it.message}", TAG)
        }
    }

    private suspend fun ensureUpdateEngineService() {
        runCatching {
            startUpdateEngineService(context)
        }.onFailure {
            logD("ensure UpdateEngine failed: ${it.message}", TAG)
        }
    }
}
