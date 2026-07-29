package io.github.lumkit.tweak.common.utils

import android.util.Log

actual object TweakLog {
    private val enabled: Boolean by lazy { isDebugBuild() }

    actual fun d(message: String, tag: String) {
        if (enabled) Log.d(tag, message)
    }

    actual fun i(message: String, tag: String) {
        if (enabled) Log.i(tag, message)
    }

    actual fun w(message: String, tag: String) {
        // warning 在 Release 也输出，便于排查 TweakServer / 特权路径
        Log.w(tag, message)
    }

    actual fun e(message: String, throwable: Throwable?, tag: String) {
        // error 始终输出（app_process 无 Application，仍需可见崩溃原因）
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
