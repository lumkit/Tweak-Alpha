package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.common.feature.UpdateEngineNotificationGate.userOpenedUpdatePageThisProcess
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 系统更新通知门闩。
 *
 * - 设置「自动监听系统更新服务」为 true：后台拉活也可发通知
 * - 为 false：仅当本进程内用户进入过 V-AB 系统更新页后才发通知
 * - 进程被杀死后 [userOpenedUpdatePageThisProcess] 复位，需再次进入页面
 */
object UpdateEngineNotificationGate {

    @Volatile
    var userOpenedUpdatePageThisProcess: Boolean = false
        private set

    fun markUserOpenedUpdatePage() {
        userOpenedUpdatePageThisProcess = true
    }

    fun shouldNotify(): Boolean {
        if (userOpenedUpdatePageThisProcess) return true
        return runBlocking {
            TweakDataStore.autoListenSystemUpdateFlow().first()
        }
    }
}
