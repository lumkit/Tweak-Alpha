package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_logo_android
import tweak_alpha.shared.generated.resources.ic_logo_background
import tweak_alpha.shared.generated.resources.ic_logo_eyes
import tweak_alpha.shared.generated.resources.ic_logo_foreground
import tweak_alpha.shared.generated.resources.ic_logo_mid

@Composable
fun Logo(
    modifier: Modifier = Modifier,
    enableAnimation: Boolean = true,
) {

    var enable by rememberSaveable(enableAnimation) { mutableStateOf(enableAnimation) }
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(enable) {
        if (!enable) return@LaunchedEffect
        var lastFrameTime = 0L
        while (isActive) {
            withFrameNanos { frameTime ->
                if (lastFrameTime != 0L) {
                    val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                    rotation = (rotation + deltaSeconds * 120f) % 360f
                }
                lastFrameTime = frameTime
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_logo_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .clickable(
                    enabled = enableAnimation,
                    indication = null,
                    interactionSource = null
                ) {
                    enable = !enable
                },
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
        )

        Image(
            painter = painterResource(Res.drawable.ic_logo_mid),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                rotationZ = rotation
            },
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.surface),
        )

        Image(
            painter = painterResource(Res.drawable.ic_logo_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primaryContainer),
        )

        Image(
            painter = painterResource(Res.drawable.ic_logo_android),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = 1f
                    translationY = 1f
                },
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.surface),
        )

        Image(
            painter = painterResource(Res.drawable.ic_logo_eyes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
        )
    }
}

@Preview
@Composable
private fun LogoPreview() {
    Logo(Modifier.size(100.dp))
}