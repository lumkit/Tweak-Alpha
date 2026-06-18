package io.github.lumkit.tweak.common.utils

import androidx.compose.runtime.Composable
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberLayerBackdropColor(): LayerBackdrop {
    val backgroundColor = MiuixTheme.colorScheme.surface

    return rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
}
