package io.github.lumkit.tweak.common.utils

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.lumkit.tweak.application
import java.io.File

actual infix fun String.joinPath(childPath: String): String {
    return File(this, childPath).absolutePath
}

actual fun Uri.documentFile(): DocumentFile? {
    return DocumentFile.fromSingleUri(application, this)
}