package io.github.lumkit.tweak.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.LiquidBottomTab
import io.github.lumkit.tweak.common.component.LiquidBottomTabs
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.ui.screen.category.FuncCategoryPage
import io.github.lumkit.tweak.ui.screen.info.InfoPage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainScreen(
    viewModel: MainViewModel = viewModel { MainViewModel() }
) {
    val backdrop = rememberLayerBackdropColor()
    val pagerState = rememberPagerState(initialPage = 1) { viewModel.pagerCount }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        MainScreenContent(backdrop, viewModel, pagerState)
        NavBar(backdrop, viewModel) {
            scope.launch {
                pagerState.animateScrollToPage(it)
            }
        }
    }
}

@Composable
private fun BoxScope.NavBar(backdrop: LayerBackdrop, viewModel: MainViewModel, onTabSelected: (Int) -> Unit) {
    val density = LocalDensity.current

    val selectedTabIndex by viewModel.selectedTabIndex.collectAsStateWithLifecycle()
    val contentColor = MiuixTheme.colorScheme.onSurface

    LiquidBottomTabs(
        selectedTabIndex = { selectedTabIndex },
        onTabSelected = {
            viewModel.setSelectedTabIndex(it)
            onTabSelected(it)
        },
        backdrop = backdrop,
        tabsCount = viewModel.pagerCount,
        modifier = Modifier.padding(horizontal = 48f.dp)
            .padding(bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + 16.dp)
            .align(Alignment.BottomCenter)
    ) {
        repeat(viewModel.pagerCount) { index ->
            val page = remember(index) { MainPages.entries[index] }

            LiquidBottomTab(
                onClick = {
                    viewModel.setSelectedTabIndex(index)
                }
            ) {
                Box(
                    Modifier
                        .size(28f.dp)
                        .paint(painterResource(page.iconRes), colorFilter = ColorFilter.tint(contentColor))
                )
                Text(
                    text = stringResource(page.titleRes),
                    style = MiuixTheme.textStyles.footnote2,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun MainScreenContent(
    backdrop: LayerBackdrop,
    viewModel: MainViewModel,
    pagerState: PagerState
) {

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .onEach {
                viewModel.setSelectedTabIndex(it)
            }.launchIn(this)
    }

    ScreenSurface(
        modifier = Modifier.layerBackdrop(backdrop)
            .fillMaxSize()
    ) {
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState
        ) {
            val page = remember(it) { MainPages.entries[it] }

            when (page) {
                MainPages.Func -> FuncCategoryPage()
                MainPages.Info -> InfoPage()
            }
        }
    }
}