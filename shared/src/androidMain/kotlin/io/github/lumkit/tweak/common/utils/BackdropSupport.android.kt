package io.github.lumkit.tweak.common.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
actual fun isAdvancedBackdropEffectSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
actual fun isAdvancedRenderEffectSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}