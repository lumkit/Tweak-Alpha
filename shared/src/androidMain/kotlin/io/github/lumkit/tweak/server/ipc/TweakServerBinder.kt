package io.github.lumkit.tweak.server.ipc

import android.os.Handler
import android.os.Process
import android.system.Os
import androidx.annotation.Keep
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.server.ITweakServer

/**
 * App ↔ tweak_server Binder。
 * [stop] 只负责投递“自行退出”请求；真正 teardown 在主线程执行，避免 Binder 线程上
 * [kotlin.system.exitProcess] 导致客户端收不到事务完成（Root→Shizuku 后只能靠这条路径关进程）。
 */
@Keep
class TweakServerBinder(
    private val version: String,
    private val packageName: String,
    private val mainHandler: Handler,
    private val statusExtra: () -> String = { "battery_enabled=0 a11y_enabled=0" },
    private val onStop: () -> Unit,
    private val onReload: () -> Unit = {},
) : ITweakServer.Stub() {

    @Volatile
    private var stopRequested = false

    override fun ping(): String = "PONG"

    override fun status(): String {
        val extra = statusExtra().trim()
        return buildString {
            append("OK")
            append(" running=1")
            append(" pid=").append(Process.myPid())
            append(" uid=").append(Os.getuid())
            append(" version=").append(version)
            append(" package=").append(packageName)
            if (extra.isNotEmpty()) {
                append(' ').append(extra)
            }
            if (!extra.contains("a11y_enabled=")) {
                append(" a11y_enabled=0")
            }
            append(" binder=1")
            append('\n')
        }
    }

    override fun stop() {
        if (stopRequested) {
            logD("stop already requested", TAG)
            return
        }
        stopRequested = true
        logD("stop requested by client, schedule self-exit on main", TAG)
        // 先让 Binder 事务返回，再在主线程做 teardown / exitProcess
        mainHandler.post {
            runCatching(onStop)
                .onFailure { logD("onStop failed: ${it.message}", TAG) }
        }
    }

    override fun reloadConfig() {
        onReload()
    }

    companion object {
        private const val TAG = "TweakServerBinder"
    }
}
