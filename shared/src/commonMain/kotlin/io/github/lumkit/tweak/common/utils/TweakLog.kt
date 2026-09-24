package io.github.lumkit.tweak.common.utils

/**
 * 日志工具。
 * [d] 与 [i] 只在 Debug 构建输出；[w] 与 [e] 在 Release 中仍然输出。
 */
expect object TweakLog {
    fun d(message: String, tag: String = "Tweak_Log")
    fun i(message: String, tag: String = "Tweak_Log")
    fun w(message: String, tag: String = "Tweak_Log")
    fun e(message: String, throwable: Throwable? = null, tag: String = "Tweak_Log")
}

private const val APP_TAG = "Tweak_Log"

fun logD(message: String, tag: String = "") {
    TweakLog.d(message, APP_TAG tag tag)
}

fun logI(message: String, tag: String = "") {
    TweakLog.i(message, APP_TAG tag tag)
}

fun logW(message: String, tag: String = "") {
    TweakLog.w(message, APP_TAG tag tag)
}

fun logE(message: String, throwable: Throwable? = null, tag: String = "") {
    TweakLog.e(message, throwable, APP_TAG tag tag)
}

private infix fun String.tag(tag: String = ""): String = if (tag.isEmpty()) {
    this
} else {
    "${this}_$tag"
}