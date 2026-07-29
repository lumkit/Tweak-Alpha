package io.github.lumkit.tweak.server.ipc

import android.os.Process
import android.system.Os
import androidx.annotation.Keep
import io.github.lumkit.tweak.server.ITweakServer

@Keep
class TweakServerBinder(
    private val version: String,
    private val packageName: String,
    private val statusExtra: () -> String = { "battery_enabled=0 a11y_enabled=0" },
    private val onStop: () -> Unit,
    private val onReload: () -> Unit = {},
) : ITweakServer.Stub() {

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
        onStop()
    }

    override fun reloadConfig() {
        onReload()
    }
}
