package io.github.lumkit.tweak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.common.utils.AccessibilityBootstrap
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机广播：仅在开启自启动且特权通过时启用无障碍服务。
 * KeepAlive / UpdateEngine 由无障碍服务连接后拉起。
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
                        if (autoStart) {
                            val enabled = AccessibilityBootstrap.enableIfPrivileged()
                            logD("ensure accessibility: $enabled", TAG)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
