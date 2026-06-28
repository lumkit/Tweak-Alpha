package io.github.lumkit.tweak.common.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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

private val powerManager by lazy {
    application.getSystemService(Context.POWER_SERVICE) as PowerManager
}

actual fun isIgnoringBatteryOptimizations(): Boolean {
    return powerManager.isIgnoringBatteryOptimizations(application.packageName)
}

@SuppressLint("BatteryLife")
actual fun trySetIsIgnoringBatteryOptimizations() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.data = "package:${application.packageName}".toUri()
    application.startActivity(intent)
}

actual fun hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
actual fun ManagedActivityResultLauncher<String, Boolean>.requestNotificationPermission() {
    logD("requestNotificationPermission")
    this.launch(Manifest.permission.POST_NOTIFICATIONS)
}

actual fun areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(application).areNotificationsEnabled()
actual fun jumpToAppInfo() {
    logD("jumpToAppInfo")
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${application.packageName}".toUri()
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    application.startActivity(intent)
}