package io.github.lumkit.tweak.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.github.lumkit.tweak.MainActivity
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.LibSuX
import io.github.lumkit.tweak.common.utils.ShizukuX
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logI
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        const val KEEP_ALIVE_NOTIFICATION_CHANNEL_NAME = "Tweak-Alpha-Keep-Alive"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotification()
        }
        startPersistentTaskService()
        checkRuntime()
        logD("KeepAliveService.onCreate", TAG)
    }

    private fun checkRuntime() {
        scope.launch {
            GlobalViewModel.runtimeModeState.collect {
                logD("runtime mode is $it", TAG)
                when (it) {
                    RuntimeMode.Root -> {
                        val hasRoot = try {
                            LibSuX.checkRoot()
                        } catch (e: Exception) {
                            logD(e.stackTraceToString())
                            false
                        }
                        logD("has root: $hasRoot", TAG)

                        if (hasRoot) {
                            logD("startAccessibilityService", TAG)
                            startAccessibilityService()
                        }
                    }
                    RuntimeMode.Shizuku -> {
                        val checkShizukuPermission = ShizukuX.checkShizuku()
                        logD("has shizuku: $checkShizukuPermission", TAG)

                        if (checkShizukuPermission) {
                            logD("startAccessibilityService", TAG)
                            startAccessibilityService()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun startAccessibilityService() {
        val packageName = application.packageName
        val component = "$packageName/io.github.lumkit.tweak.service.TweakAccessibilityService"

        // 先禁用再启用，确保系统重新初始化服务（解决部分 ROM 的事件监听限制）
        val current = ReusableShells.execSync("settings get secure enabled_accessibility_services").trim()

        // 如果当前已包含该服务，先移除再重新添加，强制系统重新绑定
        val cleaned = if (current.contains(component)) {
            current.split(":").filter { it != component }.joinToString(":")
        } else {
            current
        }

        // 重新构建包含该服务的列表
        val newValue = if (cleaned.isNotEmpty() && cleaned != "null") {
            "$cleaned:$component"
        } else {
            component
        }

        ReusableShells.execSync("settings put secure enabled_accessibility_services '$newValue'")
        ReusableShells.execSync("settings put secure accessibility_enabled 1")

        // MIUI/HyperOS: 将服务加入已允许的无障碍服务列表
        val miuiCurrent = ReusableShells.execSync("settings get secure permitted_accessibility_services").trim()
        if (!miuiCurrent.contains(component)) {
            val miuiNewValue = if (miuiCurrent.isNotEmpty() && miuiCurrent != "null") {
                "$miuiCurrent:$component"
            } else {
                component
            }
            ReusableShells.execSync("settings put secure permitted_accessibility_services '$miuiNewValue'")
            logI("permitted_accessibility_services=$miuiNewValue", TAG)
        }

        logI("enabled_accessibility_services=$newValue", TAG)
        val check = checkAccessibilityService()
        logI("aas check: $check", TAG)
    }

    suspend fun checkAccessibilityService(): Boolean = withContext(Dispatchers.IO) {
        ReusableShells.execSync("settings get secure enabled_accessibility_services").contains(application.packageName)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun startPersistentTaskService() {
        scope.launch {
            val intent = Intent(this@KeepAliveService, PersistentTaskService::class.java)
            intent.action = "EXTRA_KEEP_ALIVE"
            while (isActive) {
//                logD("KeepAliveService.startPersistentTaskService", TAG)
                startService(intent)
                delay(5000.milliseconds)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun Context.createNotification() {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(
            KEEP_ALIVE_NOTIFICATION_CHANNEL_NAME,
            KEEP_ALIVE_NOTIFICATION_CHANNEL_NAME,
            importance
        )
        val notificationManager = this.getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationManager.createNotificationChannel(channel)
        channel.description = getSystemService(NotificationManager::class.java).toString()
        //在创建的通知渠道上发送通知
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, KEEP_ALIVE_NOTIFICATION_CHANNEL_NAME)
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_MUTABLE
        )
        builder.setSmallIcon(R.mipmap.ic_logo_round) //设置通知图标
            .setContentTitle(getString(R.string.text_keep_alive)) //设置通知标题
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setOngoing(true)
        notificationManager.createNotificationChannel(channel)
        startForeground(121382, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}