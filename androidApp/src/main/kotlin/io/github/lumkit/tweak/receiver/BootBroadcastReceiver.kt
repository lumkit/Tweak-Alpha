package io.github.lumkit.tweak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.startSmartService
import io.github.lumkit.tweak.service.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootBroadcastReceiver: BroadcastReceiver() {

    companion object {
        private const val TAG = "BootBroadcastReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                logD("BootBroadcastReceiver.onReceive: $intent", TAG)
                scope.launch {
                    TweakDataStore.autoStartAppSwitchFlow().collect { switch ->
                        logD("autoStartAppSwitchFlow: $switch", TAG)
                        if (switch) {
                            // 启动Daemon
                            context.startSmartService(KeepAliveService::class.java)
                        }
                    }
                }
            }
        }
    }

}