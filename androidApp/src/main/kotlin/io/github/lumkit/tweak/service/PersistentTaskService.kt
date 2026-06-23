package io.github.lumkit.tweak.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import io.github.lumkit.tweak.common.utils.logD

class PersistentTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PersistentTaskService"
        const val NOTIFICATION_CHANNEL_NAME_DEFAULT = "Tweak-Alpha"
        const val NOTIFICATION_CHANNEL_ID_DEFAULT = "tweak-alpha"
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()

        initService()
    }

    private fun initService() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        initNotification()
    }

    private fun initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = NOTIFICATION_CHANNEL_ID_DEFAULT
            val channelName = NOTIFICATION_CHANNEL_NAME_DEFAULT
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("DefaultLocale")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logD("PersistentTaskService.onStartCommand: ${intent?.action}", TAG)
        when (intent?.action) {
            "EXTRA_KEEP_ALIVE" -> Unit
        }
        return START_STICKY
    }
}