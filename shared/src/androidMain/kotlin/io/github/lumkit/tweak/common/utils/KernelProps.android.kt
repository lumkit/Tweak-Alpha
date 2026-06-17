package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.sharednative.SystemPropertyBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object KernelProps {
    actual suspend fun getProp(propName: String): String {
        return Files.readText(propName)
            .getOrNull()
            ?.trim()
            .orEmpty()
    }

    actual suspend fun getSystemProp(propName: String): String {
        return withContext(Dispatchers.IO) {
            SystemPropertyBridge.get(propName).trim()
        }
    }
}
