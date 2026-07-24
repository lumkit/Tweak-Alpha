package io.github.lumkit.tweak.common.base

import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

abstract class BaseService: Service() {

    private val startCommandState: Int
        get() = runBlocking {
            if (TweakDataStore.autoStartAppSwitchFlow().first()) {
                START_STICKY
            } else {
                START_NOT_STICKY
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return startCommandState
    }

    /**
     * Android 14+ shortService / Android 15+ dataSync 等限时前台服务超时回调。
     * 必须尽快 [stopSelf]，否则系统会抛出
     * [android.app.RemoteServiceException.ForegroundServiceDidNotStopInTimeException]。
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        handleForegroundServiceTimeout(startId, fgsType = null)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundServiceTimeout(startId, fgsType)
    }

    /**
     * 前台服务超时处理。子类可覆盖以取消任务，但最终必须调用 [stopSelf]。
     */
    protected open fun handleForegroundServiceTimeout(startId: Int, fgsType: Int?) {
        logE(
            "Foreground service timed out startId=$startId fgsType=$fgsType, stopSelf()",
            null,
            javaClass.simpleName,
        )
        runCatching { onForegroundTimeoutCleanup() }
        stopSelf()
    }

    /**
     * 超时前的清理钩子（取消协程、降级通知等）。默认空实现。
     */
    protected open fun onForegroundTimeoutCleanup() = Unit
}
