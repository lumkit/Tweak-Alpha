package io.github.lumkit.tweak.server.battery

import android.os.IBinder
import io.github.lumkit.tweak.common.utils.logW

object ScreenStateReader {

    private const val TAG = "ScreenStateReader"

    fun isInteractive(): Boolean {
        return runCatching {
            val raw = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "power") as? IBinder
                ?: return false
            val pm = Class.forName("android.os.IPowerManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, raw)
                ?: return false
            val method = pm.javaClass.methods.firstOrNull {
                it.name == "isInteractive" && it.parameterTypes.isEmpty()
            } ?: return false
            method.invoke(pm) as? Boolean ?: false
        }.onFailure {
            logW("isInteractive failed: ${it.message}", TAG)
        }.getOrDefault(false)
    }
}
