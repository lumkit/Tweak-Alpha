package io.github.lumkit.tweak.common.utils

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build

fun Context.startService(service: Class<out Service>) {
    val intent = Intent(this, service)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}