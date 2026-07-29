package io.github.lumkit.tweak.server.ipc

import android.content.AttributionSource
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.system.Os
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.server.fakecontext.FakeContext

/**
 * 将 Server Binder 投递到 App 的 TweakBinderProvider
 *（反射 getContentProviderExternal，避免完整 Hidden API stub）。
 */
object BinderDelivery {

    private const val TAG = "BinderDelivery"

    fun sendToApp(packageName: String, serverBinder: IBinder): Boolean {
        val authority = TweakBinderContract.authority(packageName)
        return runCatching {
            val am = activityManagerProxy() ?: error("IActivityManager null")
            val token = FakeContext.providerToken
            val holder = invokeGetContentProviderExternal(am, authority, token)
                ?: error("ContentProviderHolder null for $authority")
            val provider = holderProvider(holder) ?: error("provider null")
            val extras = Bundle().apply {
                putBinder(TweakBinderContract.EXTRA_BINDER, serverBinder)
            }
            invokeProviderCall(provider, packageName, authority, extras)
            logD("sendToApp authority=$authority ok", TAG)
            true
        }.onFailure {
            logE("sendToApp failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    private fun activityManagerProxy(): Any? {
        val sm = Class.forName("android.os.ServiceManager")
        val getService = sm.getMethod("getService", String::class.java)
        val raw = getService.invoke(null, "activity") as? IBinder ?: return null
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, raw)
    }

    private fun invokeGetContentProviderExternal(
        am: Any,
        authority: String,
        token: IBinder,
    ): Any? {
        val methods = am.javaClass.methods.filter { it.name == "getContentProviderExternal" }
        for (m in methods.sortedByDescending { it.parameterTypes.size }) {
            val params = m.parameterTypes
            val args = when (params.size) {
                4 -> arrayOf(authority, 0, token, authority)
                3 -> arrayOf(authority, 0, token)
                else -> continue
            }
            val result = runCatching { m.invoke(am, *args) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun holderProvider(holder: Any): Any? {
        return runCatching {
            holder.javaClass.getField("provider").get(holder)
        }.getOrElse {
            runCatching {
                holder.javaClass.getMethod("getProvider").invoke(holder)
            }.getOrNull()
        }
    }

    private fun invokeProviderCall(
        provider: Any,
        packageName: String,
        authority: String,
        extras: Bundle,
    ): Bundle? {
        val methodName = TweakBinderContract.METHOD_SET_BINDER
        val methods = provider.javaClass.methods.filter { it.name == "call" }
        for (m in methods.sortedByDescending { it.parameterTypes.size }) {
            val invoked = runCatching {
                when (m.parameterTypes.size) {
                    5 -> {
                        val first = m.parameterTypes[0]
                        if (Build.VERSION.SDK_INT >= 31 && first.name.contains("AttributionSource")) {
                            val source = AttributionSource.Builder(Os.getuid())
                                .setPackageName(if (Os.getuid() == 0) "root" else "com.android.shell")
                                .build()
                            m.invoke(provider, source, authority, methodName, null, extras)
                        } else {
                            m.invoke(provider, packageName, authority, methodName, null, extras)
                        }
                    }
                    6 -> m.invoke(provider, packageName, null, authority, methodName, null, extras)
                    4 -> m.invoke(provider, packageName, methodName, null, extras)
                    else -> null
                } as Bundle?
            }
            if (invoked.isSuccess) {
                return invoked.getOrNull() ?: Bundle.EMPTY
            }
        }
        error("no compatible IContentProvider.call overload")
    }
}
