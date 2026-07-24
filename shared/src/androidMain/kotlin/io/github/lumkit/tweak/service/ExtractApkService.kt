package io.github.lumkit.tweak.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.lumkit.tweak.MainActivity
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.base.BaseService
import io.github.lumkit.tweak.common.utils.AppOperationResult
import io.github.lumkit.tweak.common.utils.AppsHelper
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.startSmartService
import io.github.lumkit.tweak.shared.R
import io.github.lumkit.tweak.ui.screen.appManager.ExtractApkSession
import io.github.lumkit.tweak.ui.screen.appManager.ExtractApkTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_extract_apk_batch_failed
import tweak_alpha.shared.generated.resources.text_extract_apk_batch_partial
import tweak_alpha.shared.generated.resources.text_extract_apk_batch_success
import tweak_alpha.shared.generated.resources.text_extract_apk_failed
import tweak_alpha.shared.generated.resources.text_extract_apk_running
import tweak_alpha.shared.generated.resources.text_extract_apk_success

class ExtractApkService : BaseService() {

    companion object {
        private const val TAG = "ExtractApkService"
        private const val CHANNEL_ID = "TweakAlphaExtractApkService"
        private const val NOTIFICATION_ID = 1200

        internal const val ACTION_START = "io.github.lumkit.tweak.service.ExtractApkService.ACTION_START"
        internal const val EXTRA_PACKAGE_NAMES = "EXTRA_PACKAGE_NAMES"
        internal const val EXTRA_APP_NAMES = "EXTRA_APP_NAMES"
        internal const val EXTRA_TARGET_DIR = "EXTRA_TARGET_DIR"

        fun start(tasks: List<ExtractApkTask>, targetDir: String) {
            if (tasks.isEmpty()) return
            val intent = Intent(application, ExtractApkService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(
                    EXTRA_PACKAGE_NAMES,
                    ArrayList(tasks.map { it.packageName }),
                )
                putStringArrayListExtra(
                    EXTRA_APP_NAMES,
                    ArrayList(tasks.map { it.appName }),
                )
                putExtra(EXTRA_TARGET_DIR, targetDir)
            }
            application.startSmartService(intent)
        }
    }

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logE(throwable.stackTraceToString(), throwable, TAG)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + serviceExceptionHandler)
    private var extractJob: Job? = null
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val packageNames = intent.getStringArrayListExtra(EXTRA_PACKAGE_NAMES).orEmpty()
                val appNames = intent.getStringArrayListExtra(EXTRA_APP_NAMES).orEmpty()
                val targetDir = intent.getStringExtra(EXTRA_TARGET_DIR).orEmpty()
                val tasks = packageNames.mapIndexedNotNull { index, packageName ->
                    packageName.takeIf(String::isNotBlank)?.let { pkg ->
                        ExtractApkTask(
                            packageName = pkg,
                            appName = appNames.getOrNull(index).orEmpty().ifBlank { pkg },
                        )
                    }
                }
                if (tasks.isEmpty() || targetDir.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val label = if (tasks.size == 1) {
                    tasks.first().appName.ifBlank { tasks.first().packageName }
                } else {
                    "${tasks.size}"
                }
                tryStartForeground(label)
                startExtract(tasks, targetDir)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startExtract(tasks: List<ExtractApkTask>, targetDir: String) {
        if (extractJob?.isActive == true) {
            return
        }
        extractJob = serviceScope.launch {
            val outputDir = targetDir.trimEnd('/')
            val runningText = getString(Res.string.text_extract_apk_running)
            ExtractApkSession.begin(
                tasks = tasks,
                targetDir = targetDir,
                outputDir = outputDir,
                message = runningText,
            )
            val notificationTitle = getString(R.string.text_extract_apk_notification_title)
            notifyProgress(
                title = notificationTitle,
                text = runningText,
                progress = 0,
            )

            var lastFailureMessage = ""
            for ((index, task) in tasks.withIndex()) {
                ExtractApkSession.startTask(index)
                val displayName = task.appName.ifBlank { task.packageName }
                val result = try {
                    AppsHelper.extractApk(task.packageName, targetDir) { copied, total, fileName ->
                        val percent = if (total > 0L) {
                            ((copied.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        val message = "$percent% · $fileName"
                        ExtractApkSession.updateTaskProgress(percent, fileName, message)
                        val session = ExtractApkSession.state.value
                        notifyProgress(
                            title = notificationTitle,
                            text = "$displayName · ${session.overallProgress}% (${index + 1}/${tasks.size})",
                            progress = session.overallProgress,
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    AppOperationResult.Failure(throwable.message ?: "提取失败")
                }
                if (result is AppOperationResult.Failure) {
                    lastFailureMessage = result.message
                }
                ExtractApkSession.completeTask(success = result is AppOperationResult.Success)
            }

            val session = ExtractApkSession.state.value
            val message = buildFinishMessage(
                total = tasks.size,
                successCount = session.successCount,
                failureCount = session.failureCount,
                outputDir = outputDir,
                lastFailureMessage = lastFailureMessage,
            )
            ExtractApkSession.finish(message)
            notifyMessage(message)

            stopForegroundCompat()
            stopSelf()
        }
    }

    private suspend fun buildFinishMessage(
        total: Int,
        successCount: Int,
        failureCount: Int,
        outputDir: String,
        lastFailureMessage: String,
    ): String {
        return when {
            total <= 1 && successCount == 1 -> {
                getString(Res.string.text_extract_apk_success, outputDir)
            }
            total <= 1 && failureCount >= 1 -> {
                getString(
                    Res.string.text_extract_apk_failed,
                    lastFailureMessage.ifBlank { "未知错误" },
                )
            }
            failureCount == 0 -> {
                getString(Res.string.text_extract_apk_batch_success, successCount, outputDir)
            }
            successCount == 0 -> {
                getString(Res.string.text_extract_apk_batch_failed)
            }
            else -> {
                getString(
                    Res.string.text_extract_apk_batch_partial,
                    successCount,
                    failureCount,
                    outputDir,
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_extract_apk),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_extract_apk_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun tryStartForeground(label: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.text_extract_apk_notification_title))
            .setContentText(label)
            .setSmallIcon(R.mipmap.ic_logo_round)
            .setContentIntent(mainPendingIntent())
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyProgress(title: String, text: String, progress: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_logo_round)
            .setContentIntent(mainPendingIntent())
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyMessage(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_logo_round)
            .setContentIntent(mainPendingIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            1201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onForegroundTimeoutCleanup() {
        extractJob?.cancel()
        runCatching { stopForegroundCompat() }
    }

    override fun onDestroy() {
        extractJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
