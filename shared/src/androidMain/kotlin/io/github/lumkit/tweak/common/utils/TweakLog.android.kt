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
        if (enabled) Log.w(tag, message)
    }

    actual fun e(message: String, throwable: Throwable?, tag: String) {
        if (enabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
