package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    subTitle: String = "",
    scrollBehavior: ScrollBehavior,
    backdrop: Backdrop,
    navigationIcon: @Composable (() -> Unit) = {},
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    val background = MiuixTheme.colorScheme.surface
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }

    TopAppBar(
        title = title,
        largeTitle = largeTitle,
        subtitle = subTitle,
        scrollBehavior = scrollBehavior,
        color = if (advancedBackdropEffectSupported) {
            Color.Transparent
        } else {
            background
        },
        navigationIcon = navigationIcon,
        actions = actions,
        modifier = modifier.fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = {
                    RoundedCornerShape(0.dp)
                },
                effects = {
                    if (advancedBackdropEffectSupported) {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(8f.dp.toPx(), 8f.dp.toPx())
                    }
                },
                shadow = { Shadow(radius = 0.dp) },
                onDrawSurface = {
                    if (advancedBackdropEffectSupported) {
                        drawRect(background.copy(.4f))
                    } else {
                        drawRect(background)
                    }
                },
                highlight = { null }
            )
    )
}