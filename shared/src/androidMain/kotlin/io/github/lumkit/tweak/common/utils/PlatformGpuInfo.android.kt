package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.sharednative.GpuInfoBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual object PlatformGpuInfo {
    actual suspend fun gles(): String {
        return withContext(Dispatchers.IO) {
            GpuInfoBridge.getGlesInfo().trim()
        }
    }
}
