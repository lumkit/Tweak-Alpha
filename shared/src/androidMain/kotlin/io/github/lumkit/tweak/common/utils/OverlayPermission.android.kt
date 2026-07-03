package io.github.lumkit.tweak.common.utils

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import io.github.lumkit.tweak.application

actual fun canDrawOverlays(): Boolean {
    return Settings.canDrawOverlays(application)
}

@Composable
actual fun rememberRequestOverlayPermission(onGranted: () -> Unit): () -> Unit {
    val currentOnGranted = rememberUpdatedState(onGranted)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (canDrawOverlays()) {
            currentOnGranted.value()
        }
    }

    return {
        if (canDrawOverlays()) {
            currentOnGranted.value()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${application.packageName}".toUri()
            )
            launcher.launch(intent)
        }
    }
}
