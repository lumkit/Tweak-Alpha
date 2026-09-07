package io.github.lumkit.tweak.common.feature

import android.content.Context
import android.content.Intent
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.service.UpdateEngineService
import kotlinx.coroutines.flow.first

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

actual suspend fun ensureUpdateEngineService() {
    startUpdateEngineService(application)
}

/**
 * 使用指定 [Context] 启动更新服务。
 */
suspend fun startUpdateEngineService(context: Context) {
    val runtimeMode = TweakDataStore.runtimeModeFlow().first()
    val support = UpdateEngineClient.support()
    logD("runtimeMode: $runtimeMode, support: $support", TAG)
    if (runtimeMode == RuntimeMode.Root && support) {
        context.startService(Intent(context, UpdateEngineService::class.java))
    }
}
