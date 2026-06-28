package io.github.lumkit.tweak.common

import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.getOrNull
import kotlinx.coroutines.runBlocking
import java.io.File

actual object Const {
    actual object Path {
        private const val PATH_ID_CACHE = "cache"

        actual val cachePath: String
            get() {
                val file = File(application.filesDir, PATH_ID_CACHE)
                if (!file.exists()) {
                    file.mkdirs()
                }
                return file.absolutePath
            }

        actual val otaPackage: String
            get() {
                val path = "/data/ota_package"
                val exists = runBlocking { Files.exists(path).getOrNull() ?: false }
                if (!exists) {
                    runBlocking {
                        Files.mkdirs(path)
                    }
                }

                return path
            }
    }
}