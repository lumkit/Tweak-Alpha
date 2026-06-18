package io.github.lumkit.tweak.common.utils

import androidx.compose.runtime.mutableStateOf

expect object ShizukuX {
    val isShizukuAvailable: Boolean
    suspend fun checkShizuku(): Boolean
    suspend fun requestPermission(): Boolean
}

internal object ShizukuState {
    val isGranted = mutableStateOf(false)
}