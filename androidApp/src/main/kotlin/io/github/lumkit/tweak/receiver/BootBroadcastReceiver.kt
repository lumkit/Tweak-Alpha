package io.github.lumkit.tweak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.common.utils.AccessibilityBootstrap
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机广播：自启开启且运行模式特权可用、用户开启了无障碍选项时，
 * 若服务未存活则通过 [AccessibilityBootstrap] 拉起。
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

                        val mode = TweakDataStore.runtimeModeFlow().first()
                        if (mode == RuntimeMode.Unknow) {
                            logD("runtime mode unknown, skip boot accessibility", TAG)
                            return@launch
                        }

                        val ensured = AccessibilityBootstrap.ensureRunningIfUserEnabled()
                        logD("ensure accessibility if user enabled => $ensured", TAG)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
