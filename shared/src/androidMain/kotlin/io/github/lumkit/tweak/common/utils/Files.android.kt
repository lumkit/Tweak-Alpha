package io.github.lumkit.tweak.common.utils

import java.io.File

actual infix fun String.joinPath(childPath: String): String {
    return File(this, childPath).absolutePath
}