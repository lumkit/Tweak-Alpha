package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 特权常驻进程（TweakServer）启停统一入口。
 * 受 [TweakDataStore.nativeDaemonEnabledFlow] 控制；无障碍 connected / 特权就绪时仅在启用时拉活。
 */
object NativeDaemonController {

    private const val TAG = "NativeDaemonController"

    suspend fun setEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        TweakDataStore.setNativeDaemonEnabled(enabled)
        if (enabled) {
            ensureRunning()
        } else {
            stop()
        }
    }

    /** 若用户已启用，检测存活并在必要时启动 */
    suspend fun ensureRunningIfEnabled(): Boolean = withContext(Dispatchers.IO) {
        if (!TweakDataStore.nativeDaemonEnabledFlow().first() || !TweakDataStore.autoStartAppSwitchFlow().first()) {
            logD("native daemon disabled or auto start disabled, skip", TAG)
            return@withContext false
        }
        ensureRunning()
    }

    suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            A11yWatchDaemonConfig.syncFromDataStore()
            BatteryRecordDaemonConfig.syncFromDataStore()
            val status = TweakDaemon.status()
            val needsUpgrade =
                status == null ||
                    !status.version.contains("c2") ||
                    !status.raw.contains("binder=1")
            if (needsUpgrade && TweakDaemon.isRunning()) {
                TweakDaemon.stop()
            }
            val started = TweakDaemon.start()
            // 已在跑时 start 会早退，仍需让 Server 重读刚写入的 conf
            TweakDaemon.reloadConfig()
            logD("ensureRunning => $started needsUpgrade=$needsUpgrade", TAG)
            started
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("ensureRunning failed: ${e.message}", e, TAG)
            false
        }
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val stopped = TweakDaemon.stop()
            logD("stop => $stopped", TAG)
            stopped
        }.onFailure {
            logE("stop failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }
}
