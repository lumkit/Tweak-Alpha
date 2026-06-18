package io.github.lumkit.tweak.common.utils

/**
 * 日志工具类。
 * 仅在 Debug 模式下输出日志，Release 模式下所有调用为空操作。
 */
expect object TweakLog {
    fun d(message: String, tag: String = "Tweak")
    fun i(message: String, tag: String = "Tweak")
    fun w(message: String, tag: String = "Tweak")
    fun e(message: String, throwable: Throwable? = null, tag: String = "Tweak")
}

fun logD(message: String, tag: String = "Tweak") {
    TweakLog.d(message, tag)
}

fun logI(message: String, tag: String = "Tweak") {
    TweakLog.i(message, tag)
}

fun logW(message: String, tag: String = "Tweak") {
    TweakLog.w(message, tag)
}

fun logE(message: String, throwable: Throwable? = null, tag: String = "Tweak") {
    TweakLog.e(message, throwable, tag)
}
