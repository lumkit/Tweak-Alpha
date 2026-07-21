package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.toSize
import com.kyant.backdrop.Backdrop
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
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
            .glassBlur(backdrop)
    )
}

@Composable
fun SmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subTitle: String = "",
    scrollBehavior: ScrollBehavior,
    backdrop: Backdrop,
    navigationIcon: @Composable (() -> Unit) = {},
    actions: @Composable (RowScope.() -> Unit) = {},
    expander: @Composable (ColumnScope.() -> Unit)? = null,
    onSizeChanged: (DpSize) -> Unit = {},
) {
    val background = MiuixTheme.colorScheme.surface
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }
    val density = LocalDensity.current

    Column(
        modifier = modifier.fillMaxWidth()
            .glassBlur(backdrop)
            .onSizeChanged {
                with(density) { onSizeChanged(it.toSize().toDpSize()) }
            }
    ) {
        SmallTopAppBar(
            title = title,
            subtitle = subTitle,
            scrollBehavior = scrollBehavior,
            color = if (advancedBackdropEffectSupported) {
                Color.Transparent
            } else {
                background
            },
            navigationIcon = navigationIcon,
            actions = actions,
            modifier = Modifier.fillMaxWidth()
        )

        expander?.invoke(this)
    }
}