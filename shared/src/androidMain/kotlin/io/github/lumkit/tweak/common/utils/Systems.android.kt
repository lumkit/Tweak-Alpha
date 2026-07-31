package io.github.lumkit.tweak.common.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.daemon.NativeDaemonController
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.service.KeepAliveService
import io.github.lumkit.tweak.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

actual fun isDebugBuild(): Boolean {
    // app_process / TweakServer 没有 Application，不能碰 lateinit application
    runCatching {
        return (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    // Daemon：通过系统 Context 查 App 是否 debuggable，否则 logD 全被吞掉
    return runCatching {
        val atClass = Class.forName("android.app.ActivityThread")
        var thread = atClass.getMethod("currentActivityThread").invoke(null)
        if (thread == null) {
            thread = atClass.getMethod("systemMain").invoke(null)
        }
        val ctx = atClass.getMethod("getSystemContext").invoke(thread) as? Context
            ?: return@runCatching false
        val ai = ctx.packageManager.getApplicationInfo("io.github.lumkit.tweak", 0)
        (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(false)
}

actual fun restartApp() {
//    runCatching { shutdownOwnedProcessesForRestart() }

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

actual fun exitApp() {
    Process.killProcess(Process.myPid())
    exitProcess(0)
}

/**
 * 重启前清理我们拉起的进程：
 * 1. Native Daemon（`tweak_server`）
 * 2. Root / Shizuku 特权文件服务子进程（如 `:file_service`）
 * 3. 同 UID 下其它 App 子进程
 */
private fun shutdownOwnedProcessesForRestart() {
    runBlocking {
        withContext(Dispatchers.IO) {
            runCatching { NativeDaemonController.stop() }
            runCatching { releasePrivilegedFileServices() }
            runCatching { killDetachedOwnedProcessesByShell() }
        }
    }
    killSiblingAppProcesses()
}

private suspend fun killDetachedOwnedProcessesByShell() {
    val pkg = application.packageName
    // Shizuku UserService 常以 shell uid 运行，App 侧 Process.killProcess 杀不掉
    ReusableShells.execSync(
        "pids=\$(ps -A -o PID=,NAME= 2>/dev/null | awk -v n=\"$pkg:file_service\" '\$2==n {print \$1}'); " +
            "if [ -z \"\$pids\" ]; then pids=\$(pidof \"$pkg:file_service\" 2>/dev/null); fi; " +
            "if [ -n \"\$pids\" ]; then kill -TERM \$pids 2>/dev/null; kill -KILL \$pids 2>/dev/null; fi; " +
            "pids=\$(pidof tweak_server 2>/dev/null); " +
            "if [ -n \"\$pids\" ]; then kill -TERM \$pids 2>/dev/null; kill -KILL \$pids 2>/dev/null; fi",
    )
}

private fun killSiblingAppProcesses() {
    val myPid = Process.myPid()
    val pkg = application.packageName
    val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.runningAppProcesses
        ?.asSequence()
        ?.filter { info ->
            info.pid != myPid &&
                (info.processName == pkg || info.processName.startsWith("$pkg:"))
        }
        ?.forEach { info ->
            runCatching { Process.killProcess(info.pid) }
        }
}

actual val SDK_INT: Int
    get() = Build.VERSION.SDK_INT

actual val SDK_RELEASE: String
    get() = Build.VERSION.RELEASE

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
    jumpToAppInfo(application.packageName)
}

actual fun jumpToAppInfo(packageName: String) {
    logD("jumpToAppInfo: $packageName")
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:$packageName".toUri()
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    application.startActivity(intent)
}

fun Context.isTablet(): Boolean {
    return resources.configuration.smallestScreenWidthDp >= 600
}

fun Context.isPhone(): Boolean {
    return !isTablet()
}

fun isEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk", true)
            || Build.MODEL.contains("Emulator", true)
            || Build.MODEL.contains("Android SDK built for", true)
            || Build.MANUFACTURER.contains("Genymotion", true)
            || Build.BRAND.startsWith("generic")
            || Build.DEVICE.startsWith("generic")
            || Build.PRODUCT.contains("sdk", true)
            || Build.PRODUCT.contains("emulator", true)
            || Build.HARDWARE.contains("goldfish", true)
            || Build.HARDWARE.contains("ranchu", true)
            || Build.HARDWARE.contains("cutf_cvm", true)
}

actual fun getDeviceType(): DeviceType {
    // 模拟器优先判断
    if (isEmulator()) {
        return DeviceType.EMULATOR
    }

    val pm = application.packageManager

    // Android TV / Google TV
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    ) {
        return DeviceType.TV
    }

    // Wear OS
    if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
        return DeviceType.WATCH
    }

    // Android Automotive
    if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
        return DeviceType.AUTOMOTIVE
    }

    // UiMode 作为补充判断
    val uiModeManager = application.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    when (uiModeManager?.currentModeType) {
        Configuration.UI_MODE_TYPE_TELEVISION -> return DeviceType.TV
        Configuration.UI_MODE_TYPE_WATCH -> return DeviceType.WATCH
        Configuration.UI_MODE_TYPE_CAR -> return DeviceType.AUTOMOTIVE
    }

    // Phone / Tablet
    return if (application.resources.configuration.smallestScreenWidthDp >= 600) {
        DeviceType.TABLET
    } else {
        DeviceType.PHONE
    }
}

actual suspend fun getDeviceModel(): String {
    val marketName = KernelProps.getSystemProp("ro.product.marketname")
    return if (marketName.isBlank()) {
        "$BRAND $MODEL"
    } else {
        if (marketName.contains(BRAND, true)) {
            marketName
        } else {
            "$BRAND $marketName"
        }
    }
}

actual val BRAND: String
    get() = Build.BRAND
actual val MODEL: String
    get() = Build.MODEL

actual fun getDeviceScreenWidth(): Int {
    return getDeviceScreenMetrics().widthPixels
}

actual fun getDeviceScreenHeight(): Int {
    return getDeviceScreenMetrics().heightPixels
}

private val windowManager by lazy {
    application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
}

@Suppress("DEPRECATION")
actual fun getDeviceScreenRefreshRate(): Float {
    return windowManager.defaultDisplay?.refreshRate?.takeIf { it > 0f } ?: 60f
}

@Suppress("DEPRECATION")
private fun getDeviceScreenMetrics(): DisplayMetrics {
    return DisplayMetrics().also { metrics ->
        windowManager.defaultDisplay.getRealMetrics(metrics)
    }
}

actual val packageName: String
    get() = application.packageName

actual fun startKeepAliveService(isForegroundService: Boolean) {
    val intent = Intent(application, KeepAliveService::class.java)
    application.startService(intent)
}

actual fun toastText(msg: String) {
    Handler(Looper.getMainLooper()).post {
        if (isProcessInForeground()) {
            Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
        } else {
            notifyFromBackground(msg)
        }
    }
}

actual fun copyTextToClipboard(text: String, label: String) {
    val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun isProcessInForeground(): Boolean {
    val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val importance = am.runningAppProcesses
        ?.firstOrNull { it.pid == Process.myPid() }
        ?.importance
    return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

private fun notifyFromBackground(msg: String) {
    if (!NotificationManagerCompat.from(application).areNotificationsEnabled()) {
        Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
        return
    }
    val channelId = "tweak_alpha_background_tip"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Tweak-Alpha Tips",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(application, channelId)
        .setSmallIcon(R.mipmap.ic_logo_round)
        .setContentTitle(application.applicationInfo.loadLabel(application.packageManager).toString())
        .setContentText(msg)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(application).notify(msg.hashCode(), notification)
}

actual val BUILD_VERSION_CODE: Long
    get() {
        val packageInfo = application.packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
actual val BUILD_VERSION_NAME: String
    get() = application.packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.data = url.toUri()

    try {
        application.startActivity(intent)
    } catch (e: Exception) {
        logD(e.stackTraceToString())
    }
}
