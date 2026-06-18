package io.github.lumkit.tweak.common.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

actual object ShizukuX {
    
    actual val isShizukuAvailable: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

    actual suspend fun checkShizuku(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            // 检查 Shizuku 是否运行
            if (!Shizuku.pingBinder()) {
                ShizukuState.isGranted.value = false
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            // 检查权限
            val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            ShizukuState.isGranted.value = granted
            continuation.resume(granted)
        } catch (e: Exception) {
            ShizukuState.isGranted.value = false
            continuation.resume(false)
        }
    }

    actual suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            if (!Shizuku.pingBinder()) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ShizukuState.isGranted.value = true
                continuation.resume(true)
                return@suspendCancellableCoroutine
            }

            // 请求权限
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ShizukuState.isGranted.value = granted
                    if (continuation.isActive) {
                        continuation.resume(granted)
                    }
                }
            }

            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(0)

            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
        } catch (e: Exception) {
            ShizukuState.isGranted.value = false
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }
}
