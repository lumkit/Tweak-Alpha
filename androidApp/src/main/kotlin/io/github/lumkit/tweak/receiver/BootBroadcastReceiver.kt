package io.github.lumkit.tweak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.feature.UpdateEngineNotificationGate
import io.github.lumkit.tweak.common.feature.ensureUpdateEngineService
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机广播：自启开启且运行模式特权可用时，按需拉起 Native Daemon。
 */
class BootBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootBroadcastReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                logD("BootBroadcastReceiver.onReceive: $intent", TAG)
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val autoStart = TweakDataStore.autoStartAppSwitchFlow().first()
                        logD("autoStart=$autoStart", TAG)
                        if (!autoStart) return@launch

                        // 如果允许，发送更新监听通知
                        if (UpdateEngineNotificationGate.shouldNotify()) {
                            ensureUpdateEngineService()
                        }

                        val mode = TweakDataStore.runtimeModeFlow().first()
                        if (mode == RuntimeMode.Unknow) {
                            logD("runtime mode unknown, skip boot daemon", TAG)
                            return@launch
                        }

                        val started = NativeDaemonController.ensureRunningIfEnabled()
                        logD("ensure native daemon if enabled => $started", TAG)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
