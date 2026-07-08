package io.github.lumkit.tweak.common.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatDateTime(millis: Long, pattern: String): String {
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}
