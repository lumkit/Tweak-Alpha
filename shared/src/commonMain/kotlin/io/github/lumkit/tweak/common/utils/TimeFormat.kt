package io.github.lumkit.tweak.common.utils

expect fun formatDateTime(millis: Long, pattern: String = "yyyy/MM/dd HH:mm:ss"): String

fun formatElapsedTimeInternal(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun Long.formatElapsedTime(): String = formatElapsedTimeInternal(this)