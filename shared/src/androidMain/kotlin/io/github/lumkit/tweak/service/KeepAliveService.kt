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
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.shared.R

/**
 * 保活前台服务。仅负责常驻通知保活。
 */
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
        logD("KeepAliveService.onCreate", TAG)
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
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, KEEP_ALIVE_NOTIFICATION_CHANNEL_NAME)
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_MUTABLE
        )
        builder.setSmallIcon(R.mipmap.ic_logo_round)
            .setContentTitle(getString(R.string.text_keep_alive))
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setOngoing(true)
        notificationManager.createNotificationChannel(channel)
        startForeground(121382, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
