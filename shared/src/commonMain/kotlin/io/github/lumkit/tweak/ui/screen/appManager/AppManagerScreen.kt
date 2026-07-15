package io.github.lumkit.tweak.ui.screen.appManager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.component.Block
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.SmallTopAppBar
import io.github.lumkit.tweak.common.utils.AppInfo
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import io.github.lumkit.tweak.common.utils.jumpToAppInfo
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_all_apps_fill
import tweak_alpha.shared.generated.resources.ic_ice_app
import tweak_alpha.shared.generated.resources.ic_record_chart
import tweak_alpha.shared.generated.resources.ic_sun
import tweak_alpha.shared.generated.resources.ic_system_apps
import tweak_alpha.shared.generated.resources.ic_unable_app
import tweak_alpha.shared.generated.resources.ic_user_apps
import tweak_alpha.shared.generated.resources.text_abi_32
import tweak_alpha.shared.generated.resources.text_abi_64
import tweak_alpha.shared.generated.resources.text_all_apps
import tweak_alpha.shared.generated.resources.text_app_manager
import tweak_alpha.shared.generated.resources.text_app_manager_description
import tweak_alpha.shared.generated.resources.text_app_uninstall
import tweak_alpha.shared.generated.resources.text_dialog_cancel
import tweak_alpha.shared.generated.resources.text_dialog_force_stop_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_force_stop_confirm
import tweak_alpha.shared.generated.resources.text_dialog_freeze_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_unfreeze_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_uninstall_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_warm_tip
import tweak_alpha.shared.generated.resources.text_enable_app
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_force_kill_app
import tweak_alpha.shared.generated.resources.text_no_native_abi
import tweak_alpha.shared.generated.resources.text_system_apps
import tweak_alpha.shared.generated.resources.text_unable_app
import tweak_alpha.shared.generated.resources.text_unabled_apps
import tweak_alpha.shared.generated.resources.text_user_apps

internal val AppManagerProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "AppManagerProvider",
            title = Res.string.text_app_manager,
            icon = Res.drawable.ic_record_chart,
            description = Res.string.text_app_manager_description,
            capabilities = setOf(
                Capability.SHIZUKU_OR_ROOT,
            ),
            route = Screen.AppManager,
            defaultState = FeatureState.ENABLED,
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            }
        )

    @Composable
    override fun Content() {
        AppManagerContent()
    }

}

@Composable
private fun AppManagerContent(
    viewModel: AppManagerViewModel = viewModel { AppManagerViewModel() }
) {
    val navigator = LocalNavigator.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var bottomToolBarHeight by remember { mutableStateOf(0.dp) }
    val direction = LocalLayoutDirection.current
    val hostState = LocalSnackBarHostState.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val pages = remember(viewModel) {
        listOf(
            AppPage(
                iconRes = Res.drawable.ic_all_apps_fill,
                titleRes = Res.string.text_all_apps,
                listState = viewModel.allApps,
            ),
            AppPage(
                iconRes = Res.drawable.ic_system_apps,
                titleRes = Res.string.text_system_apps,
                listState = viewModel.systemApps,
            ),
            AppPage(
                iconRes = Res.drawable.ic_user_apps,
                titleRes = Res.string.text_user_apps,
                listState = viewModel.userApps,
            ),
            AppPage(
                iconRes = Res.drawable.ic_ice_app,
                titleRes = Res.string.text_unabled_apps,
                listState = viewModel.unabledApps,
            ),
        )
    }

    val pager = rememberPagerState { pages.size }
    val selectMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val loadingTextRes by viewModel.loadingTextRes.collectAsStateWithLifecycle()

    BackHandler(selectMode) {
        viewModel.setSelectedMode(false)
    }

    LaunchedEffect(selectMode) {
        if (!selectMode) {
            viewModel.cleanSelectedPackageNames()
        }
    }

    viewModel.LoadStateLaunchEffect {
        Watch("forceKillSelectedApps") {
            it.message?.let { msg ->
                if (msg.isNotBlank()) {
                    hostState.showSnackbar(it.message ?: "")
                }
            }
        }
        Watch("unableSelectedApps") {
            it.message?.let { msg ->
                if (msg.isNotBlank()) {
                    hostState.showSnackbar(it.message ?: "")
                }
            }
        }
        Watch("enableSelectedApps") {
            it.message?.let { msg ->
                if (msg.isNotBlank()) {
                    hostState.showSnackbar(it.message ?: "")
                }
            }
        }
        Watch("uninstallSelectedApps") {
            it.message?.let { msg ->
                if (msg.isNotBlank()) {
                    hostState.showSnackbar(it.message ?: "")
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncAppsOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    WindowDialog(
        show = loadingState,
        enableWindowDim = true,
        onDismissRequest = {

        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfiniteProgressIndicator()

            Text(
                text = stringResource(loadingTextRes),
                style = MiuixTheme.textStyles.main
            )
        }
    }

    ScreenSurface {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = stringResource(Res.string.text_app_manager),
                    subTitle = if (selectMode) "" else {
                        val page = remember(pager.currentPage) { pages[pager.currentPage] }
                        val list by page.listState.collectAsStateWithLifecycle()
                        buildString {
                            append(stringResource(page.titleRes))
                            append(": ")
                            append("${list.size} Apps")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (!selectMode) {
                                    navigator.goBack()
                                } else {
                                    viewModel.setSelectedMode(false)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectMode) {
                                    MiuixIcons.Close
                                } else {
                                    MiuixIcons.Back
                                },
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (selectMode) {
                            // 全选按钮
                            Block {
                                val hasSelectedAll by viewModel.hasSelectedAllPackageNames.collectAsStateWithLifecycle()
                                val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()
                                val parentState = when {
                                    selectedApps.isEmpty() -> ToggleableState.Off
                                    hasSelectedAll -> ToggleableState.On
                                    else -> ToggleableState.Indeterminate
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.toggleSelectedAllApps()
                                    }
                                ) {
                                    Checkbox(
                                        state = parentState,
                                        onClick = null
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingToolbar = {
                val selectMode by viewModel.selectedMode.collectAsStateWithLifecycle()
                Column {
                    AnimatedVisibility(
                        visible = !selectMode
                    ) {
                        FloatingToolBar(
                            pages = pages,
                            pagerState = pager,
                        ) {
                            bottomToolBarHeight = it
                        }
                    }
                }
            },
            floatingToolbarPosition = ToolbarPosition.BottomCenter,
            containerColor = MiuixTheme.colorScheme.surface,
        ) { paddingValues ->

            val listPaddingValues = remember(bottomToolBarHeight) {
                PaddingValues(
                    start = paddingValues.calculateStartPadding(direction),
                    end = paddingValues.calculateEndPadding(direction),
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + bottomToolBarHeight,
                )
            }

            Box {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize()
                        .layerBackdrop(backdrop),
                    state = pager,
                    userScrollEnabled = !selectMode,
                ) {
                    val page = remember(it) { pages[it] }

                    AppItems(
                        page,
                        listPaddingValues,
                        scrollBehavior,
                        direction,
                        viewModel.selectedMode,
                        viewModel.selectedApps,
                        onTap = { appInfo ->
                            if (!viewModel.selectedMode.value) {
                                jumpToAppInfo(appInfo.packageName)
                            } else {
                                viewModel.toggleSelectedAppInfo(appInfo.packageName)
                            }
                        },
                        onLongClick = { appInfo ->
                            if (!viewModel.selectedMode.value) {
                                viewModel.setSelectedMode(true)
                                viewModel.setSelectableAppPackageNames(page.listState.value.map { item -> item.packageName }
                                    .toSet())
                                viewModel.toggleSelectedAppInfo(appInfo.packageName)
                            }
                        }
                    )
                }

                AnimatedContent(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    targetState = selectMode,
                    transitionSpec = {
                        if (targetState) {
                            // 展开
                            slideInVertically(
                                initialOffsetY = { it }
                            ) togetherWith slideOutVertically(
                                targetOffsetY = { -it / 4 }
                            )
                        } else {
                            // 折叠
                            slideInVertically(
                                initialOffsetY = { -it / 4 }
                            ) togetherWith slideOutVertically(
                                targetOffsetY = { it }
                            )
                        }.using(
                            SizeTransform(clip = false)
                        )
                    }
                ) { visible ->
                    if (visible) {
                        BottomToolbar(backdrop, viewModel, pager)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.BottomToolbar(
    backdrop: LayerBackdrop,
    viewModel: AppManagerViewModel,
    pagerState: PagerState,
) {
    val background = MiuixTheme.colorScheme.surface
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }
    val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()
    val isEmpty = remember(selectedApps) { selectedApps.isEmpty() }
    val currentPosition = pagerState.currentPage
    val isUnfreezeAction = currentPosition == 3
    var forceDialogState by remember { mutableStateOf(false) }
    var iceDialogState by remember { mutableStateOf(false) }
    var uninstallDialogState by remember { mutableStateOf(false) }

    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = stringResource(Res.string.text_dialog_force_stop_app_summary),
        show = forceDialogState,
        onDismissRequest = {
            forceDialogState = false
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.forceKillSelectedApps()
                    forceDialogState = false
                },
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(text = stringResource(Res.string.text_dialog_force_stop_confirm))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    forceDialogState = false
                }
            ) {
                Text(text = stringResource(Res.string.text_dialog_cancel))
            }
        }
    }

    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = if (isUnfreezeAction) {
            stringResource(Res.string.text_dialog_unfreeze_app_summary)
        } else {
            stringResource(Res.string.text_dialog_freeze_app_summary)
        },
        show = iceDialogState,
        onDismissRequest = {
            iceDialogState = false
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (isUnfreezeAction) {
                        viewModel.enableSelectedApps()
                    } else {
                        viewModel.unableSelectedApps()
                    }
                    iceDialogState = false
                },
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(
                    text = if (isUnfreezeAction) {
                        stringResource(Res.string.text_enable_app)
                    } else {
                        stringResource(Res.string.text_unable_app)
                    }
                )
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    iceDialogState = false
                }
            ) {
                Text(text = stringResource(Res.string.text_dialog_cancel))
            }
        }
    }

    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = stringResource(Res.string.text_dialog_uninstall_app_summary),
        show = uninstallDialogState,
        onDismissRequest = {
            uninstallDialogState = false
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.uninstallSelectedApps()
                    uninstallDialogState = false
                },
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(text = stringResource(Res.string.text_app_uninstall))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    uninstallDialogState = false
                }
            ) {
                Text(text = stringResource(Res.string.text_dialog_cancel))
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter)
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
            .background(
                if (advancedBackdropEffectSupported) {
                    MiuixTheme.colorScheme.surfaceContainer.copy(.5f)
                } else {
                    background
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp)
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = {}
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            if (currentPosition != 3) {
                // 结束进程
                Block {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .weight(1f)
                            .alpha(if (isEmpty) .31f else 1f)
                            .clickable(
                                enabled = !isEmpty,
                            ) {
                                forceDialogState = true
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close2,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                        )
                        Text(
                            text = stringResource(Res.string.text_force_kill_app),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface.copy(.75f)
                        )
                    }
                }
            }

            // 冻结
            Block {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .weight(1f)
                        .alpha(if (isEmpty) .31f else 1f)
                        .clickable(
                            enabled = !isEmpty,
                        ) {
                            iceDialogState = true
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = if (currentPosition == 3) {
                            painterResource(Res.drawable.ic_sun)
                        } else {
                            painterResource(Res.drawable.ic_unable_app)
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                    )
                    Text(
                        text = if (currentPosition == 3) {
                            stringResource(Res.string.text_enable_app)
                        } else {
                            stringResource(Res.string.text_unable_app)
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface.copy(.75f)
                    )
                }
            }

            // 卸载
            if (currentPosition >= 2) {
                Block {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .weight(1f)
                            .alpha(if (isEmpty) .31f else 1f)
                            .clickable(
                                enabled = !isEmpty,
                            ) {
                                uninstallDialogState = true
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                        )
                        Text(
                            text = stringResource(Res.string.text_app_uninstall),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface.copy(.75f)
                        )
                    }
                }
            }
        }
    }


}

@Composable
private fun FloatingToolBar(
    pagerState: PagerState,
    pages: List<AppPage>,
    onToolBarHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    FloatingToolbar(
        modifier = Modifier.onSizeChanged {
            onToolBarHeight(with(density) { it.height.toDp() })
        }
    ) {
        NavToolbar(
            pages,
            pagerState,
        ) {
            scope.launch {
                pagerState.animateScrollToPage(it)
            }
        }
    }
}

@Immutable
private data class AppPage(
    val iconRes: DrawableResource,
    val titleRes: StringResource,
    val listState: StateFlow<List<AppInfo>>,
)

@Composable
private fun NavToolbar(
    list: List<AppPage>,
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { // 或 Column
        list.onEachIndexed { index, page ->
            val color by animateColorAsState(
                targetValue = if (pagerState.currentPage == index) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(.31f)
                },
                label = "color-$index"
            )

            TooltipBox(text = stringResource(page.titleRes)) {
                IconButton(
                    onClick = {
                        onTabSelected(index)
                    }
                ) {
                    Icon(
                        painter = painterResource(page.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = color
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItems(
    page: AppPage,
    paddingValues: PaddingValues,
    scrollBehavior: ScrollBehavior,
    direction: LayoutDirection,
    selectModeState: StateFlow<Boolean>,
    selectedAppsState: StateFlow<Set<String>>,
    onTap: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    val apps by page.listState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = paddingValues.calculateStartPadding(direction) + 16.dp,
            top = paddingValues.calculateTopPadding(),
            end = paddingValues.calculateEndPadding(direction) + 16.dp,
            bottom = paddingValues.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(apps) { info ->
            AppItem(info, selectModeState, selectedAppsState, onTap, onLongClick)
        }
    }
}

@Composable
private fun AppItem(
    appInfo: AppInfo,
    selectModeState: StateFlow<Boolean>,
    selectedAppsState: StateFlow<Set<String>>,
    onTap: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    val abiMap by remember(appInfo) {
        derivedStateOf {
            when {
                appInfo.abiList.isEmpty() -> Res.string.text_no_native_abi
                appInfo.abiList.firstOrNull()?.bitSize == 64 -> Res.string.text_abi_64
                else -> Res.string.text_abi_32
            }
        }
    }
    val selectedApps by selectedAppsState.collectAsStateWithLifecycle()
    val selectMode by selectModeState.collectAsStateWithLifecycle()
    val selected = appInfo.packageName in selectedApps

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(Rectangle.copy(cornerRadius = 16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = {
                    onTap(appInfo)
                },
                onLongClick = {
                    onLongClick(appInfo)
                }.takeIf { !selectMode }
            )
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            startAction = {
                AsyncImage(
                    model = appInfo.iconPath,
                    contentDescription = null,
                    error = null,
                    modifier = Modifier.clip(Rectangle.copy(cornerRadius = 12.dp))
                        .size(48.dp)
                )
            },
            endActions = {
                if (selectMode) {
                    Checkbox(
                        state = if (selected) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        },
                        onClick = null
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        ) {
            Text(
                text = appInfo.appName,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
            )

            Text(
                modifier = Modifier,
                text = buildString {
                    append(appInfo.packageName)
                    append('\n')
                    append(appInfo.versionName)
                    append(" (${appInfo.versionCode})")
                    append("\nTarget: ${appInfo.targetSdk}, Min: ${appInfo.minSdk}")
                    append(", ${stringResource(abiMap)}")
                },
                color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                ),
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
