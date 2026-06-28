package io.github.lumkit.tweak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.feature.UpdateEngineClient
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.startSmartService
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.service.KeepAliveService
import io.github.lumkit.tweak.service.UpdateEngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
                    TweakDataStore.autoStartAppSwitchFlow()
                        .distinctUntilChanged()
                        .collect { switch ->
                        logD("autoStartAppSwitchFlow: $switch", TAG)
                        if (switch) {
                            // 启动Daemon
                            context.startSmartService(KeepAliveService::class.java)

                            // 配置更新服务
                            setupUpdateService()
                        }
                    }
                }
            }
        }
    }


    private fun CoroutineScope.setupUpdateService() {
        launch {
            // 如果是Root模式并且支持OTA则启动更新服务
            val runtimeMode = GlobalViewModel.runtimeModeState.filterNotNull().first()

            val support = UpdateEngineClient.support()

            logD("runtimeMode: $runtimeMode, support: $support", TAG)

            if (runtimeMode == RuntimeMode.Root && support) {
                val intent = Intent(application, UpdateEngineService::class.java)
                application.startSmartService(intent)
            }
        }
    }
}