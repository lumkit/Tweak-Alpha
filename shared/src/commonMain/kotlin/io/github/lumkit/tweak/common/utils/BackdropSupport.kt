package io.github.lumkit.tweak.common.utils

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

expect fun isAdvancedBackdropEffectSupported(): Boolean
expect fun isAdvancedRenderEffectSupported(): Boolean

@Stable
@Composable
fun Modifier.shadowMask(radius: Dp): Modifier = if (isAdvancedRenderEffectSupported()) {
    this.blur(radius)
} else {
    this.background(color = MiuixTheme.colorScheme.onSurface.copy(.11f))
}