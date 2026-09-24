package io.github.lumkit.tweak.ui.screen.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.kyant.backdrop.shadow.Shadow
import io.github.lumkit.tweak.common.component.BottomNavigationBar
import io.github.lumkit.tweak.common.component.BottomNavigationBarItem
import io.github.lumkit.tweak.common.component.LiquidBottomTab
import io.github.lumkit.tweak.common.component.LiquidBottomTabs
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.glassBlur
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.model.AppearanceSettingsStore
import io.github.lumkit.tweak.model.NavigationViewModel
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.ui.screen.feature.FeaturePage
import io.github.lumkit.tweak.ui.screen.info.InfoPage
import io.github.lumkit.tweak.ui.screen.settings.SettingsPage
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
    val navigator = LocalNavigator.current

    Box(modifier = Modifier.fillMaxSize()) {
        MainScreenContent(backdrop, pagerState)
        NavBar(pagerState, backdrop, viewModel) {
            scope.launch {
                pagerState.animateScrollToPage(it)
            }
        }
    }

    LaunchedEffect(NavigationViewModel) {
        NavigationViewModel.setupNavigator(navigator)
    }
}

@Composable
private fun BoxScope.NavBar(
    pagerState: PagerState,
    backdrop: LayerBackdrop,
    viewModel: MainViewModel,
    onTabSelected: (Int) -> Unit
) {
    val density = LocalDensity.current
    val contentColor = MiuixTheme.colorScheme.onSurface
    val enabledFloatNavBar by AppearanceSettingsStore.enabledFloatNavBar.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = enabledFloatNavBar,
        modifier = Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter),
        contentAlignment = Alignment.BottomCenter,
        transitionSpec = {
            if (targetState) {
                slideInVertically(
                    initialOffsetY = { it }
                ) togetherWith slideOutVertically(
                    targetOffsetY = { -it / 4 }
                )
            } else {
                slideInVertically(
                    initialOffsetY = { -it / 4 }
                ) togetherWith slideOutVertically(
                    targetOffsetY = { it }
                )
            }.using(
                SizeTransform(clip = false)
            )
        }
    ) { enabled ->
        if (enabled) {
            LiquidBottomTabs(
                selectedTabIndex = { pagerState.currentPage },
                onTabSelected = { index, isUserChange ->
                    if (isUserChange) {
                        onTabSelected(index)
                    }
                },
                backdrop = backdrop,
                tabsCount = viewModel.pagerCount,
                modifier = Modifier.padding(horizontal = 48f.dp)
                    .padding(bottom = with(density) {
                        WindowInsets.navigationBars.getBottom(this).toDp()
                    } + 16.dp)
            ) {
                repeat(viewModel.pagerCount) { index ->
                    val page = remember(index) { MainPages.entries[index] }

                    LiquidBottomTab(
                        onClick = {
                            onTabSelected(index)
                        }
                    ) {
                        Box(
                            Modifier
                                .size(28f.dp)
                                .paint(
                                    painterResource(page.iconRes),
                                    colorFilter = ColorFilter.tint(contentColor)
                                )
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
    }

    AnimatedContent(
        targetState = !enabledFloatNavBar,
        modifier = Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter),
        contentAlignment = Alignment.BottomCenter,
        transitionSpec = {
            if (targetState) {
                slideInVertically(
                    initialOffsetY = { it }
                ) togetherWith slideOutVertically(
                    targetOffsetY = { -it / 4 }
                )
            } else {
                slideInVertically(
                    initialOffsetY = { -it / 4 }
                ) togetherWith slideOutVertically(
                    targetOffsetY = { it }
                )
            }.using(
                SizeTransform(clip = false)
            )
        }
    ) { enabled ->
        if (enabled) {
            BottomNavBar(
                modifier = Modifier.fillMaxWidth()
                    .glassBlur(backdrop, shadow = Shadow(radius = 4.dp)),
                currentPage = pagerState.currentPage,
                pageSize = viewModel.pagerCount,
                onTabSelected = onTabSelected,
            )
        }
    }
}

@Composable
private fun BottomNavBar(
    modifier: Modifier = Modifier,
    currentPage: Int,
    pageSize: Int,
    onTabSelected: (Int) -> Unit,
) {
    BottomNavigationBar(
        modifier = modifier
    ) {
        repeat(pageSize) { index ->
            val page = remember(index) { MainPages.entries[index] }
            BottomNavigationBarItem(
                currentPage = currentPage,
                index = index,
                iconPainter = painterResource(page.iconRes),
                title = stringResource(page.titleRes),
                onTabSelected = onTabSelected,
            )
        }
    }
}

@Composable
private fun MainScreenContent(
    backdrop: LayerBackdrop,
    pagerState: PagerState
) {
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
                MainPages.Func -> FeaturePage()
                MainPages.Info -> InfoPage()
                MainPages.Settings -> SettingsPage()
            }
        }
    }
}