package io.github.lumkit.tweak.server.fakecontext

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.system.Os
import androidx.annotation.Keep
import androidx.annotation.RequiresApi

@Keep
class FakeContext : ContextWrapper(systemContext) {

    override fun getPackageName(): String =
        if (Os.getuid() == 0) "root" else "com.android.shell"

    override fun getOpPackageName(): String = packageName

    @RequiresApi(31)
    override fun getAttributionSource(): AttributionSource {
        return AttributionSource.Builder(Os.getuid())
            .setPackageName(packageName)
            .build()
    }

    override fun getApplicationContext(): Context = this

    @Keep
    @Suppress("unused")
    fun createApplicationContext(application: ApplicationInfo, flags: Int): Context = this

    override fun createPackageContext(packageName: String, flags: Int): Context = this

    companion object {
        val providerToken = Binder()

        val systemContext: Context by lazy {
            resolveSystemContext()
        }

        private fun resolveSystemContext(): Context {
            val atClass = Class.forName("android.app.ActivityThread")
            var thread = atClass.getMethod("currentActivityThread").invoke(null)
            if (thread == null) {
                thread = atClass.getMethod("systemMain").invoke(null)
            }
            requireNotNull(thread) { "ActivityThread unavailable" }
            val ctx = atClass.getMethod("getSystemContext").invoke(thread) as? Context
                ?: atClass.getField("mSystemContext").get(thread) as? Context
            return requireNotNull(ctx) { "systemContext unavailable" }
        }
    }
}
