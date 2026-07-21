package io.github.lumkit.tweak.common.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 28.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(NavigationBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(horizontalPadding))
            content()
            Spacer(modifier = Modifier.width(horizontalPadding))
        }
    }
}


@Composable
fun RowScope.BottomNavigationBarItem(
    currentPage: Int,
    index: Int,
    iconPainter: Painter,
    title: String,
    onTabSelected: (Int) -> Unit,
) {
    val isSelected = index == currentPage
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurface.copy(.75f)
        }
    )

    Column(
        modifier = Modifier.padding(4.dp)
            .clip(Capsule())
            .fillMaxSize()
            .weight(1f)
            .clickable {
                onTabSelected(index)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = title,
            color = contentColor,
            style = MiuixTheme.textStyles.body2,
        )
    }
}