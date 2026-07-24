package io.github.lumkit.tweak.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import io.github.lumkit.tweak.MainActivity
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.Const
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.base.BaseService
import io.github.lumkit.tweak.common.feature.UpdateEngineClient
import io.github.lumkit.tweak.common.feature.UpdateEngineEvent
import io.github.lumkit.tweak.common.feature.UpdateEngineNotificationGate
import io.github.lumkit.tweak.common.feature.UpdateStatus
import io.github.lumkit.tweak.common.feature.asMsg
import io.github.lumkit.tweak.common.feature.asMsgText
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.documentFile
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.NavigationIntent
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.shared.R
import io.github.lumkit.tweak.ui.screen.updateSys.UpdateEngineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class UpdateEngineService: BaseService() {

    companion object {
        private const val TAG = "UpdateEngineService"

        private const val CHANNEL_ID = "TweakAlphaUpdateEngineService"
        private const val NOTIFICATION_ID = 1000

        internal const val ACTION_UPDATE_ROM = "io.github.lumkit.tweak.service.UpdateEngineService.ACTION_UPDATE_ROM"
        internal const val ACTION_CANCEL_UPDATE = "io.github.lumkit.tweak.service.UpdateEngineService.ACTION_CANCEL_UPDATE"
        internal const val ACTION_MERGE_UPDATE = "io.github.lumkit.tweak.service.UpdateEngineService.ACTION_MERGE_UPDATE"
        internal const val ACTION_RESET_UPDATE = "io.github.lumkit.tweak.service.UpdateEngineService.ACTION_RESET_UPDATE"
        internal const val ACTION_SUSPEND_UPDATE = "io.github.lumkit.tweak.service.UpdateEngineService.ACTION_SUSPEND_UPDATE"
        internal const val ACTION_RESUME_UPDATE = "io.github.lumkit.tweak.service.UpdateEngineService.ACTION_RESUME_UPDATE"

        internal const val EXTRA_DATA_ROM_PATH = "EXTRA_DATA_ROM_PATH"

        internal fun updateRom(path: String) {
            val intent = Intent(application, UpdateEngineService::class.java)
            intent.action = ACTION_UPDATE_ROM
            intent.putExtra(EXTRA_DATA_ROM_PATH, path)
            application.startService(intent)
        }

        fun cancelUpdate() {
            val intent = Intent(application, UpdateEngineService::class.java)
            intent.action = ACTION_CANCEL_UPDATE
            application.startService(intent)
        }

        fun mergeUpdate() {
            val intent = Intent(application, UpdateEngineService::class.java)
            intent.action = ACTION_MERGE_UPDATE
            application.startService(intent)
        }

        fun resetUpdate() {
            val intent = Intent(application, UpdateEngineService::class.java)
            intent.action = ACTION_RESET_UPDATE
            application.startService(intent)
        }

        fun suspendUpdate() {
            val intent = Intent(application, UpdateEngineService::class.java)
            intent.action = ACTION_SUSPEND_UPDATE
            application.startService(intent)
        }

        fun resumeUpdate() {
            val intent = Intent(application, UpdateEngineService::class.java)
            intent.action = ACTION_RESUME_UPDATE
            application.startService(intent)
        }
    }

    private var followJob: Job? = null
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        UpdateEngineClient()
    }

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    @Volatile
    private var isDataSyncForeground = false

    private val updateListener: UpdateEngineClient.OnReadLineListener = { line ->
        logD("line=$line", TAG)
        val event = UpdateEngineEvent.parse(line)
        logD("event=$event", TAG)
        when (event) {
            is UpdateEngineEvent.CommandTook -> {
                logD("commandTook=$event", TAG)
            }
            is UpdateEngineEvent.PayloadComplete -> {
                logD("payloadComplete=$event", TAG)
                val msg: String = event.errorCode.asMsg()

                notifyMessage(
                    title = getString(R.string.text_update_rom_title),
                    text = msg,
                    id = 1
                )
                demoteFromForeground(cancelProgressNotification = true)
            }
            is UpdateEngineEvent.StatusUpdate -> {
                logD("statusUpdate=$event", TAG)
                val status = event.status
                syncForegroundWithStatus(status)
                val percent = (status.progress.coerceIn(0f, 1f) * 100).roundToInt()
                val indeterminate = status.progress <= 0f || status.progress >= 1f

                val msg: String = status.asMsg()
                if (status is UpdateStatus.UpdatedNeedReboot || status is UpdateStatus.Idle) {
                    notifyMessage(
                        title = getString(R.string.text_update_rom_title),
                        text = msg,
                        id = 2,
                    )
                } else {
                    notifyProgress(
                        title = getString(R.string.text_update_rom_updating),
                        text = msg,
                        progress = percent,
                        indeterminate = indeterminate,
                        autoCancel = false,
                        id = 2,
                    )
                }
            }
            null -> Unit
        }
        UpdateEngineViewModel.setUpdateEvent(event)
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 空闲监听不要长期占用 dataSync FGS（Android 15+ 24h 内累计 6h 超时）
        logD("start update service (background watch)", TAG)

        updateScope.launch {
            GlobalViewModel.runtimeModeState.collect { mode ->
                if (mode != RuntimeMode.Root) {
                    logD("runtime mode is $mode, skip follow", TAG)
                    return@collect
                }

                client.setOnReadLineListener(updateListener)
                client.watch()
                followJob = launch {
                    logD("follow update status", TAG)
                    client.follow()
                }
            }
        }
    }

    override fun handleForegroundServiceTimeout(startId: Int, fgsType: Int?) {
        logE(
            "dataSync FGS timed out startId=$startId fgsType=$fgsType, stop and restart as background",
            null,
            TAG,
        )
        isDataSyncForeground = false
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        val appCtx = applicationContext
        // 下一帧再拉起普通后台服务，继续 follow，避免占用 dataSync 配额
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                appCtx.startService(Intent(appCtx, UpdateEngineService::class.java))
            }
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        logD("action: $action", TAG)
        when (action) {
            ACTION_UPDATE_ROM -> {
                updateScope.launch {
                    val status = UpdateEngineViewModel.updateStatus.value
                    if (status !is UpdateStatus.Idle) {
                        logD("update status is not idle, skip update", TAG)
                        return@launch
                    }

                    val path = intent.getStringExtra(EXTRA_DATA_ROM_PATH) ?: ""
                    val exists = path.toUri().documentFile()?.exists() ?: false
                    if (!exists) {
                        logE("rom is not exists", null,TAG)
                        notifyMessage(
                            title = getString(R.string.text_update_rom_failed),
                            text = getString(R.string.text_update_rom_msg_file_is_not_exists),
                            id = 1,
                        )
                        demoteFromForeground(cancelProgressNotification = true)
                        return@launch
                    }

                    ensureDataSyncForeground()

                    // 先清理工作区
                    val otaPackage = Const.Path.otaPackage
                    if (Files.exists(otaPackage).getOrNull() == true) {
                        Files.list(otaPackage)
                            .getOrNull()?.forEach {
                                Files.delete(it, true)
                            }
                    }

                    // 先解压
                    notifyMessage(
                        title = getString(R.string.text_update_rom_title),
                         text = getString(R.string.text_update_rom_msg_unzipping),
                         id = 2,
                         autoCancel = false,
                    )
                    UpdateEngineViewModel.setUnzipping(true)
                    val dir = try {
                        UpdateEngineClient.unzipRomFromUri(path)
                    } finally {
                        UpdateEngineViewModel.setUnzipping(false)
                    }
                    val meta = Files.list(dir).getOrNull() ?: emptyList()

                    if (meta.isEmpty()) {
                        logE("unzip rom failed", null, TAG)
                        notifyMessage(
                            title = getString(R.string.text_update_rom_failed),
                             text = getString(R.string.text_update_rom_msg_unzip_failed),
                             id = 1,
                        )
                        demoteFromForeground(cancelProgressNotification = true)
                        return@launch
                    }

                    // 开始安装
                    UpdateEngineClient.installRom(dir)
                }
            }

            ACTION_CANCEL_UPDATE -> {
                updateScope.launch {
                    UpdateEngineViewModel.launchTask(
                        id = "ACTION_CANCEL_UPDATE",
                    ) {
                        loading()
                        UpdateEngineClient.cancel().trim().also {
                            if (it.contains("'")) throw RuntimeException(it.asMsgText())
                        }
                        success(getString(R.string.text_running_success))
                    }
                }
            }

            ACTION_MERGE_UPDATE -> {
                updateScope.launch {
                    UpdateEngineViewModel.launchTask(
                        id = "ACTION_MERGE_UPDATE",
                    ) {
                        loading()
                        UpdateEngineClient.merge().trim().also {
                            if (it.contains("'")) throw RuntimeException(it.asMsgText())
                        }
                        success(getString(R.string.text_running_success))
                    }
                }
            }

            ACTION_RESET_UPDATE -> {
                updateScope.launch {
                    UpdateEngineViewModel.launchTask(
                        id = "ACTION_RESET_UPDATE",
                    ) {
                        loading()
                        UpdateEngineClient.reset().trim().also {
                            if (it.contains("'")) throw RuntimeException(it.asMsgText())
                        }
                        success(getString(R.string.text_running_success))
                    }
                }
            }

            ACTION_SUSPEND_UPDATE -> {
                updateScope.launch {
                    UpdateEngineViewModel.launchTask(
                        id = "ACTION_SUSPEND_UPDATE",
                    ) {
                        loading()
                        UpdateEngineClient.suspend().trim().also {
                            if (it.contains("'")) throw RuntimeException(it.asMsgText())
                        }
                        success(getString(R.string.text_running_success))
                    }
                }
            }

            ACTION_RESUME_UPDATE -> {
                updateScope.launch {
                    UpdateEngineViewModel.launchTask(
                        id = "ACTION_RESUME_UPDATE",
                    ) {
                        loading()
                        UpdateEngineClient.resume().trim().also {
                            if (it.contains("'")) throw RuntimeException(it.asMsgText())
                        }
                        success(getString(R.string.text_running_success))
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        logD("onDestroy", TAG)
        client.removeOnReadLineListener()
        followJob?.cancel()
        followJob = null
        updateScope.cancel()
        notificationManager.cancel(NOTIFICATION_ID + 1)
        notificationManager.cancel(NOTIFICATION_ID + 2)
        super.onDestroy()
    }

    /**
     * 创建通知渠道。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_update_engine),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_update_engine_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 仅在真正执行 OTA 同步任务时提升为 dataSync 前台服务。
     * 空闲 follow 保持普通后台 Service，避免 Android 15+ 6 小时配额耗尽崩溃。
     */
    private fun ensureDataSyncForeground() {
        if (isDataSyncForeground) return
        try {
            startForegroundWithNotification()
            isDataSyncForeground = true
            logD("elevated to dataSync foreground", TAG)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                logE("dataSync FGS not allowed (quota/background), continue as background", e, TAG)
            } else {
                logE("Cannot start foreground: ${e.message}, running as background service", e, TAG)
            }
            isDataSyncForeground = false
        }
    }

    private fun demoteFromForeground(cancelProgressNotification: Boolean) {
        if (isDataSyncForeground) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            isDataSyncForeground = false
            logD("demoted from dataSync foreground", TAG)
        }
        if (cancelProgressNotification) {
            notificationManager.cancel(NOTIFICATION_ID + 2)
        }
    }

    private fun syncForegroundWithStatus(status: UpdateStatus) {
        when (status) {
            is UpdateStatus.Idle,
            is UpdateStatus.UpdatedNeedReboot,
            is UpdateStatus.Disabled,
            -> demoteFromForeground(cancelProgressNotification = false)

            is UpdateStatus.CheckingForUpdate,
            is UpdateStatus.UpdateAvailable,
            is UpdateStatus.Downloading,
            is UpdateStatus.Verifying,
            is UpdateStatus.Finalizing,
            is UpdateStatus.ReportingErrorEvent,
            is UpdateStatus.AttemptingRollback,
            is UpdateStatus.CleanupPreviousUpdate,
            is UpdateStatus.Unknown,
            -> ensureDataSyncForeground()
        }
    }

    /**
     * 启动前台服务，确保在后台时不被系统杀掉。
     */
    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo_round)
            .setContentTitle(getString(R.string.text_update_rom_title))
            .setContentText(getString(R.string.update_status_idle))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(createPendingIntentForSystemUpdate())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID + 2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID + 2, notification)
        }
    }

    /**
     * 发送带进度条的更新进度通知。
     *
     * @param title 通知标题
     * @param text 通知内容文本
     * @param progress 当前进度（0~100）
     * @param indeterminate 是否为不确定进度（如清理阶段）
     */
    fun notifyProgress(title: String, text: String, progress: Int, indeterminate: Boolean = false, autoCancel: Boolean = true, id: Int = 0) {
        if (!UpdateEngineNotificationGate.shouldNotify()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo_round)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(autoCancel)
            .setContentIntent(createPendingIntentForSystemUpdate())
            .build()
        notificationManager.notify(NOTIFICATION_ID + id, notification)
    }

    /**
     * 发送无进度条的消息通知。
     *
     * @param title 通知标题
     * @param text 通知内容文本
     * @param autoCancel 点击后是否自动取消
     */
    fun notifyMessage(title: String, text: String, autoCancel: Boolean = true, id: Int = 0) {
        if (!UpdateEngineNotificationGate.shouldNotify()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo_round)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(autoCancel)
            .setContentIntent(createPendingIntentForSystemUpdate())
            .build()
        notificationManager.notify(NOTIFICATION_ID + id, notification)
    }

    private fun createPendingIntentForSystemUpdate(): PendingIntent {
        val intentJson = NavigationIntent.encode(Screen.UpdateSystem)

        val intent = Intent(this, MainActivity::class.java).apply {
            action = ConstCommon.Navigation.ACTION_DEEPLINK_SELF
            putExtra(ConstCommon.Navigation.EXTRA_NAV_INTENT, intentJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}