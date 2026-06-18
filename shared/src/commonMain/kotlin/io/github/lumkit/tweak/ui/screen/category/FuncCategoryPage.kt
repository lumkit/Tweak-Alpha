package io.github.lumkit.tweak.ui.screen.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.nav_category

@Composable
fun FuncCategoryPage() {
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.nav_category),
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) {
        val padding = remember(it) {
            PaddingValues(
                start = it.calculateLeftPadding(direction),
                end = it.calculateRightPadding(direction),
            )
        }

        LazyColumn(
            modifier = Modifier.layerBackdrop(backdrop)
                .padding(padding)
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = it.calculateTopPadding() + 16.dp,
                bottom = it.calculateBottomPadding() + NavigationBarHeight + 28.dp,
            )
        ) {

        }
    }
}