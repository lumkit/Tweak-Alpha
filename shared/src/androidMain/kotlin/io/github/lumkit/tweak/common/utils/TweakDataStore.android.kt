package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.application
import okio.Path
import okio.Path.Companion.toPath

actual fun createDataStorePath(name: String): Path {
    return application.filesDir.resolve(name).absolutePath.toPath()
}