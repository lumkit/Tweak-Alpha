package io.github.lumkit.tweak

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.topjohnwu.superuser.Shell
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.isDebugBuild
import io.github.lumkit.tweak.model.BatteryCalibrationSync
import io.github.lumkit.tweak.model.CrashReporter
import io.github.lumkit.tweak.sharednative.BatteryBridge

lateinit var application: TweakApplication

class TweakApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        application = this

        CrashReporter.install()
        BatteryCalibrationSync.start()
        BatteryBridge.init(this)
        initLibSu()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }

    private fun initLibSu() {
        Shell.enableVerboseLogging = isDebugBuild()
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(TweakDataStore.shellTimeoutMilliseconds)
        )
    }
}

