package io.github.lumkit.tweak.common.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.lumkit.tweak.ThemeViewModel
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
expect fun rememberScreenCornerRadius(): Int

@Composable
fun ScreenSurface(
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = viewModel { ThemeViewModel() },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val screenCornerRadius = rememberScreenCornerRadius()
    val radius = remember(screenCornerRadius) { with(density) { screenCornerRadius.toDp() } }
    val enabledBackgroundImage by viewModel.enabledBackgroundImage.collectAsStateWithLifecycle()
    val backGroundImagePath by viewModel.backgroundImagePath.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.squircleClip(
            cornerRadius = radius,
        ),
        color = MiuixTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = enabledBackgroundImage,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) }
        ) { enabled ->
            if (enabled) {
                AsyncImage(
                    model = backGroundImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        content()
    }
}