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
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KeepAliveService: Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        const val KEEP_ALIVE_NOTIFICATION_CHANNEL_NAME = "Tweak-Keep-Alive"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotification()
        }
        startPersistentTaskService()
        logD("KeepAliveService.onCreate", TAG)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun startPersistentTaskService() {
        scope.launch {
//            val intent = Intent(this@KeepAliveService, PersistentTaskService::class.java)
//            while (true) {
//                startService(intent)
//                delay(5000)
//            }
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
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, KEEP_ALIVE_NOTIFICATION_CHANNEL_NAME)
        val notificationIntent = Intent(this, KeepAliveService::class.java)
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