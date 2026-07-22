package io.github.lumkit.tweak.model

import kotlinx.serialization.Serializable

@Serializable
data class CrashReport(
    val timestamp: Long,
    val threadName: String,
    val exceptionName: String,
    val message: String,
    val stackTrace: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val brand: String,
    val model: String,
    val sdkInt: Int,
    val sdkRelease: String,
) {
    fun toCopyText(): String = buildString {
        appendLine("Package: $packageName")
        appendLine("Version: $versionName ($versionCode)")
        appendLine("Device: $brand $model")
        appendLine("Android: $sdkRelease (API $sdkInt)")
        appendLine("Time: $timestamp")
        appendLine("Thread: $threadName")
        appendLine("Exception: $exceptionName")
        appendLine("Message: $message")
        appendLine()
        append(stackTrace)
    }
}
