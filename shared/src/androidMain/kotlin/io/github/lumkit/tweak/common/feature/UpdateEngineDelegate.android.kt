package io.github.lumkit.tweak.common.feature

import android.content.Intent
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.startSmartService
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.service.UpdateEngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "UpdateEngineServiceDelegate"

actual fun commitInstallRom(path: String) {
    UpdateEngineService.updateRom(path)
}

actual fun commitCancelUpdate() {
    UpdateEngineService.cancelUpdate()
}

actual fun commitMergeUpdate() {
    UpdateEngineService.mergeUpdate()
}

actual fun commitResetUpdate() {
    UpdateEngineService.resetUpdate()
}

actual fun commitSuspendUpdate() {
    UpdateEngineService.suspendUpdate()
}

actual fun commitResumeUpdate() {
    UpdateEngineService.resumeUpdate()
}

actual fun CoroutineScope.setupUpdateForegroundService() {
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