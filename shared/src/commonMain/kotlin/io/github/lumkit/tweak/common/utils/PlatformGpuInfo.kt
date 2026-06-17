package io.github.lumkit.tweak.common.utils

internal expect object PlatformGpuInfo {
    suspend fun gles(): String
}
