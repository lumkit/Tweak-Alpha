package io.github.lumkit.tweak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * 开机广播：自启开启且运行模式特权可用时，按需拉起 Native Daemon。
 *
 * 只处理 [Intent.ACTION_BOOT_COMPLETED]。[Intent.ACTION_LOCKED_BOOT_COMPLETED]
 * 发生在用户解锁前，凭证加密存储（DataStore / filesDir）不可用，阻塞读取会触发 ANR。
 */
class BootBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootBroadcastReceiver"
        private val dataStoreTimeout = 3.seconds
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        logD("BootBroadcastReceiver.onReceive: $intent", TAG)
        val pendingResult = goAsync()
        scope.launch {
            try {
                val autoStart = withTimeoutOrNull(dataStoreTimeout) {
                    TweakDataStore.autoStartAppSwitchFlow().first()
                } ?: false
                logD("autoStart=$autoStart", TAG)
                if (!autoStart) return@launch

                val mode = withTimeoutOrNull(dataStoreTimeout) {
                    TweakDataStore.runtimeModeFlow().first()
                } ?: RuntimeMode.Unknow
                if (mode == RuntimeMode.Unknow) {
                    logD("runtime mode unknown, skip boot daemon", TAG)
                    return@launch
                }

                // 拷 APK / 起 starter 可能很久，不能拖住广播直到 ANR
                scope.launch {
                    val started = NativeDaemonController.ensureRunningIfEnabled()
                    logD("ensure native daemon if enabled => $started", TAG)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
