package io.github.lumkit.tweak.common.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import io.github.lumkit.tweak.ThemeViewModel
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TopAppBar
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
        color = MiuixTheme.colorScheme.surface,
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

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    title: String,
    navigationSpace: Boolean = false,
    content: @Composable (bottomSpaceDp: Dp, safeContentPaddingValues: PaddingValues, backdrop: LayerBackdrop, scrollBehavior: ScrollBehavior) -> Unit
) {
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()

    Scaffold(
        modifier = modifier,
        topBar = {
            Topbar(title, scrollBehavior, backdrop)
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) {
        val bottom = remember(it, navigationSpace) {
            if (navigationSpace) {
                it.calculateBottomPadding() + NavigationBarHeight + 28.dp
            } else {
                it.calculateBottomPadding()
            }
        }
        val padding = remember(it) {
            PaddingValues(
                start = it.calculateLeftPadding(direction),
                end = it.calculateRightPadding(direction),
                top = it.calculateTopPadding(),
            )
        }
        content(bottom, padding, backdrop, scrollBehavior)
    }
}

@Composable
private fun Topbar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop,
) {
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }
    val background = MiuixTheme.colorScheme.surface

    TopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        color = if (advancedBackdropEffectSupported) {
            Color.Transparent
        } else {
            background
        },
        modifier = Modifier.fillMaxWidth()
            .glassBlur(backdrop)
    )
}