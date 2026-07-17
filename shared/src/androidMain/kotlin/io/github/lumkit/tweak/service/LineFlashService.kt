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
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.base.BaseService
import io.github.lumkit.tweak.common.feature.LineFlashArchiveHelper
import io.github.lumkit.tweak.common.feature.LineFlashBehavior
import io.github.lumkit.tweak.common.feature.LineFlashResult
import io.github.lumkit.tweak.common.feature.LineFlashRomUtil
import io.github.lumkit.tweak.common.feature.LineFlashScriptType
import io.github.lumkit.tweak.common.feature.LineFlashStep
import io.github.lumkit.tweak.common.feature.humanizeFlashError
import io.github.lumkit.tweak.common.feature.suggestsRebootFastboot
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.formatMemorySize
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.joinPath
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.startSmartService
import io.github.lumkit.tweak.model.NavigationIntent
import io.github.lumkit.tweak.model.NavigationIntentTargetScreen
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.shared.R
import io.github.lumkit.tweak.ui.screen.flashRom.FlashRomViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_flash_device_lost
import tweak_alpha.shared.generated.resources.text_flash_insufficient_space
import tweak_alpha.shared.generated.resources.text_flash_missing_files
import tweak_alpha.shared.generated.resources.text_flash_no_script
import tweak_alpha.shared.generated.resources.text_flash_phase_success
import tweak_alpha.shared.generated.resources.text_flash_ready_hint
import tweak_alpha.shared.generated.resources.text_flash_service_running

class LineFlashService : BaseService() {

    companion object {
        private const val TAG = "LineFlashService"
        private const val CHANNEL_ID = "TweakAlphaLineFlashService"
        private const val NOTIFICATION_ID = 1100

        internal const val ACTION_START = "io.github.lumkit.tweak.service.LineFlashService.ACTION_START"
        internal const val ACTION_CANCEL = "io.github.lumkit.tweak.service.LineFlashService.ACTION_CANCEL"

        fun start() {
            val intent = Intent(application, LineFlashService::class.java).apply {
                action = ACTION_START
            }
            application.startSmartService(intent)
        }

        fun cancel(deviceLost: Boolean = false) {
            val intent = Intent(application, LineFlashService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DEVICE_LOST, deviceLost)
            }
            application.startService(intent)
        }

        private const val EXTRA_DEVICE_LOST = "EXTRA_DEVICE_LOST"
    }

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logE(throwable.stackTraceToString(), throwable, TAG)
        runCatching {
            val message = humanizeFlashError(throwable)
            FlashRomViewModel.markError(
                message = message,
                suggestRebootFastboot = throwable.suggestsRebootFastboot(),
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + serviceExceptionHandler)
    private var pipelineJob: Job? = null
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        FlashRomViewModel.onRequestCancel = {
            cancel(deviceLost = true)
        }
        logD("LineFlashService created", TAG)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                tryStartForeground()
                startPipeline()
            }

            ACTION_CANCEL -> {
                val deviceLost = intent.getBooleanExtra(EXTRA_DEVICE_LOST, false)
                cancelPipeline(deviceLost)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startPipeline() {
        if (pipelineJob?.isActive == true) return
        val vm = FlashRomViewModel
        // 在真正解压/刷写前先锁定按钮，避免中间态仍可点击
        val phaseBeforeLock = vm.uiState.value.phase
        if (!vm.tryBeginPipeline()) return
        val startFlash = phaseBeforeLock == FlashRomViewModel.Phase.Ready ||
            phaseBeforeLock == FlashRomViewModel.Phase.Success
        pipelineJob = serviceScope.launch {
            val device = vm.targetFastbootDevice.value
            val romPath = vm.uiState.value.romPath
            try {
                require(device != null) { "未选择 Fastboot 设备" }
                require(romPath.isNotBlank()) { "未选择 ROM 路径" }

                if (startFlash) {
                    runFlash(device.fastbootDevice)
                } else {
                    runValidateThenReady(romPath)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (space: LineFlashArchiveHelper.InsufficientSpaceException) {
                val message = getString(
                    Res.string.text_flash_insufficient_space,
                    space.requiredBytes.formatMemorySize(" "),
                    space.freeBytes.formatMemorySize(" "),
                )
                vm.markError(message)
                notifyMessage(message)
            } catch (throwable: Throwable) {
                logE(throwable.stackTraceToString(), throwable, TAG)
                val message = humanizeFlashError(throwable)
                vm.markError(
                    message = message,
                    suggestRebootFastboot = throwable.suggestsRebootFastboot(),
                )
                notifyMessage(message)
            } finally {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private suspend fun runValidateThenReady(romPath: String) {
        val vm = FlashRomViewModel
        currentCoroutineContext().ensureActive()
        val workRomDir: String = if (LineFlashArchiveHelper.isArchivePath(romPath)) {
            LineFlashArchiveHelper.clearAllSessionsExcept(null)
            val sessionId = LineFlashArchiveHelper.createSessionDir()
            vm.markUnpacking(sessionId)
            notifyProgress(getString(Res.string.text_flash_service_running), "解压中")
            LineFlashArchiveHelper.extractToSession(romPath, sessionId) { msg ->
                vm.updateProgress(0, msg)
            }
        } else {
            require(Files.exists(romPath).getOrNull() == true) { "ROM 路径不存在: $romPath" }
            romPath
        }

        currentCoroutineContext().ensureActive()
        vm.markValidating(workRomDir)
        notifyProgress(getString(Res.string.text_flash_service_running), "校验中")

        val romPackage = LineFlashRomUtil.inspectRomPackage(workRomDir)
        val batScripts = romPackage.scripts
            .filter { it.type == LineFlashScriptType.BAT }
            .ifEmpty { romPackage.scripts }
        val script = batScripts.firstOrNull { it.behavior == LineFlashBehavior.CLEAN_ALL }
            ?: batScripts.firstOrNull {
                it.name.contains("flash_all", ignoreCase = true) &&
                    !it.name.contains("lock", ignoreCase = true)
            }
            ?: batScripts.firstOrNull {
                it.name.contains("flash_all", ignoreCase = true)
            }
            ?: batScripts.firstOrNull()
            ?: error("ROM 中未找到刷写脚本")

        val report = LineFlashRomUtil.validatePackage(
            romPackage = romPackage,
            script = script,
            verifyCrc = true,
            onProgress = { progress ->
                vm.updateProgress(progress.percent, progress.stepDescription)
            },
        )
        if (!report.isComplete) {
            val details = buildList {
                if (report.missingFiles.isNotEmpty()) {
                    add("缺少文件: ${report.missingFiles.joinToString()}")
                }
                val failed = report.crcResults.filterNot { it.matches }
                if (failed.isNotEmpty()) {
                    add("CRC 失败: ${failed.joinToString { it.fileName }}")
                }
            }.joinToString("; ")
            error(details.ifBlank { "ROM 校验失败" })
        }

        vm.markReady(romPackage)
        notifyMessage(getString(Res.string.text_flash_ready_hint))
    }

    private suspend fun runFlash(device: io.github.lumkit.tweak.common.feature.FastbootDevice) {
        val vm = FlashRomViewModel
        val ui = vm.uiState.value
        val romPackage = ui.romPackage ?: error("缺少已校验的 ROM 包信息")
        val script = ui.selectedScript
            ?: error(getString(Res.string.text_flash_no_script))

        val missing = script.steps.mapNotNull { step ->
            when (step) {
                is LineFlashStep.Flash -> {
                    val path = romPackage.imagesDir joinPath step.fileName
                    if (Files.exists(path).getOrNull() != true) step.fileName else null
                }
                else -> null
            }
        }
        if (missing.isNotEmpty()) {
            error(getString(Res.string.text_flash_missing_files, missing.joinToString()))
        }

        vm.markFlashing()
        notifyProgress(getString(Res.string.text_flash_service_running), "刷机中")

        val result = LineFlashRomUtil.flash(
            device = device,
            romPackage = romPackage,
            script = script,
            verifyBeforeFlash = false,
            verifyCrc = false,
            onProgress = { progress ->
                vm.onFlashProgress(progress)
                notifyProgress(
                    getString(R.string.channel_line_flash),
                    progress.stepDescription,
                    progress.percent,
                )
            },
        )

        when (result) {
            is LineFlashResult.Success -> {
                vm.markSuccess()
                notifyMessage(getString(Res.string.text_flash_phase_success))
            }

            is LineFlashResult.Failure -> {
                vm.markError(
                    message = result.message,
                    failedStepIndex = result.failedStepIndex,
                    suggestRebootFastboot = result.cause?.suggestsRebootFastboot() == true,
                )
                notifyMessage(result.message)
            }
        }
    }

    private fun cancelPipeline(deviceLost: Boolean) {
        pipelineJob?.cancel()
        pipelineJob = null
        serviceScope.launch {
            val sessionId = FlashRomViewModel.uiState.value.sessionId
            LineFlashArchiveHelper.clearSession(sessionId)
            if (deviceLost) {
                val message = getString(Res.string.text_flash_device_lost)
                FlashRomViewModel.clearForDeviceLost(message)
                notifyMessage(message)
            } else {
                FlashRomViewModel.resetAfterCancel()
            }
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_line_flash),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_line_flash_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun tryStartForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Line flash")
            .setSmallIcon(R.mipmap.ic_logo_round)
            .setContentIntent(mainPendingIntent())
            .setOngoing(true)
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

    private fun notifyProgress(title: String, text: String, progress: Int = 0) {
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

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private fun mainPendingIntent(): PendingIntent {
        val navIntent = NavigationIntent(
            targetScreen = NavigationIntentTargetScreen.FlashRom,
            screenJson = json.encodeToString(Screen.FlashRom),
        )
        val intentJson = json.encodeToString(navIntent)
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ConstCommon.Navigation.ACTION_DEEPLINK_SELF
            putExtra("nav_intent", intentJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            1101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        FlashRomViewModel.onRequestCancel = null
        pipelineJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
