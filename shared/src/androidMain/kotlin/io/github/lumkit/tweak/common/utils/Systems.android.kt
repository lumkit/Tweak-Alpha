package io.github.lumkit.tweak.common.utils

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import io.github.lumkit.tweak.application
import kotlin.system.exitProcess

actual fun isDebugBuild(): Boolean {
    return (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

actual fun restartApp() {
    val packageManager = application.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage(application.packageName)
        ?: return
    val restartIntent = Intent.makeRestartActivityTask(launchIntent.component).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    application.startActivity(restartIntent)

    Process.killProcess(Process.myPid())
    exitProcess(0)
}

actual val SDK_INT: Int
    get() = Build.VERSION.SDK_INT

actual val BOARD: String
    get() = Build.BOARD
