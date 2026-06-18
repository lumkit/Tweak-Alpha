package io.github.lumkit.tweak.common.utils

/**
 * 日志工具类。
 * 仅在 Debug 模式下输出日志，Release 模式下所有调用为空操作。
 */
expect object TweakLog {
    fun d(message: String, tag: String = "Tweak_Log")
    fun i(message: String, tag: String = "Tweak_Log")
    fun w(message: String, tag: String = "Tweak_Log")
    fun e(message: String, throwable: Throwable? = null, tag: String = "Tweak_Log")
}

fun logD(message: String, tag: String = "Tweak_Log") {
    TweakLog.d(message, tag)
}

fun logI(message: String, tag: String = "Tweak_Log") {
    TweakLog.i(message, tag)
}

fun logW(message: String, tag: String = "Tweak_Log") {
    TweakLog.w(message, tag)
}

fun logE(message: String, throwable: Throwable? = null, tag: String = "Tweak_Log") {
    TweakLog.e(message, throwable, tag)
}
