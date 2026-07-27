package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.application
import java.io.File

internal actual fun rootWorkRoot(): String {
    val dir = File(application.filesDir, "tweak-alpha")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir.absolutePath
}
