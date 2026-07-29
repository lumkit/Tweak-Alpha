package io.github.lumkit.tweak.server.ipc

import android.os.IBinder
import io.github.lumkit.tweak.server.ITweakServer
import java.util.concurrent.atomic.AtomicReference

/**
 * App 进程持有的 TweakServer Binder 连接。
 * 由 [io.github.lumkit.tweak.provider.TweakBinderProvider] 写入。
 */
object TweakServerConnection {

    private val binderRef = AtomicReference<IBinder?>(null)
    private val deathRecipient = IBinder.DeathRecipient {
        binderRef.compareAndSet(binderRef.get(), null)
    }

    @JvmStatic
    var binder: IBinder?
        get() = binderRef.get()?.takeIf { it.pingBinder() }
        set(value) {
            binderRef.getAndSet(null)?.let { old ->
                runCatching { old.unlinkToDeath(deathRecipient, 0) }
            }
            if (value != null && value.pingBinder()) {
                runCatching { value.linkToDeath(deathRecipient, 0) }
                binderRef.set(value)
            }
        }

    @JvmStatic
    val service: ITweakServer?
        get() = binder?.let { ITweakServer.Stub.asInterface(it) }

    fun clear() {
        binder = null
    }
}
