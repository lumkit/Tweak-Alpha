package io.github.lumkit.tweak.ui.screen.info

import android.app.ActivityManager
import android.content.Context
import io.github.lumkit.tweak.application

actual fun isAppInForeground(): Boolean {
    val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val importance = am.runningAppProcesses
        ?.firstOrNull { it.pid == android.os.Process.myPid() }
        ?.importance
    return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
