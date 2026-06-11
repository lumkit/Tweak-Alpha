package io.github.lumkit.tweak

import android.app.Application
import com.topjohnwu.superuser.Shell
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.isDebugBuild

lateinit var application: TweakApplication

class TweakApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        application = this

        initLibSu()
    }

    private fun initLibSu() {
        println("TweakApplication.initLibSu")
        Shell.enableVerboseLogging = isDebugBuild()
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(TweakDataStore.shellTimeoutMilliseconds)
        )
    }
}