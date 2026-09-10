package io.github.lumkit.tweak.ui.screen.appManager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.Block
import io.github.lumkit.tweak.common.component.BottomNavigationBar
import io.github.lumkit.tweak.common.component.BottomNavigationBarItem
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.SmallTopAppBar
import io.github.lumkit.tweak.common.component.glassBlur
import io.github.lumkit.tweak.common.utils.AppInfo
import io.github.lumkit.tweak.common.utils.AppState
import io.github.lumkit.tweak.common.utils.AppsHelper
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.formatDateTime
import io.github.lumkit.tweak.common.utils.formatMemorySize
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_history
import tweak_alpha.shared.generated.resources.ic_disable
import tweak_alpha.shared.generated.resources.ic_ice_app
import tweak_alpha.shared.generated.resources.ic_module
import tweak_alpha.shared.generated.resources.ic_sun
import tweak_alpha.shared.generated.resources.ic_system_apps
import tweak_alpha.shared.generated.resources.ic_unable_app
import tweak_alpha.shared.generated.resources.ic_user_apps
import tweak_alpha.shared.generated.resources.text_abi_32
import tweak_alpha.shared.generated.resources.text_abi_64
import tweak_alpha.shared.generated.resources.text_android_api_format
import tweak_alpha.shared.generated.resources.text_app_detail
import tweak_alpha.shared.generated.resources.text_app_info_apk_size
import tweak_alpha.shared.generated.resources.text_app_info_data_dir
import tweak_alpha.shared.generated.resources.text_app_info_first_install
import tweak_alpha.shared.generated.resources.text_app_info_last_update
import tweak_alpha.shared.generated.resources.text_app_info_min_sdk
import tweak_alpha.shared.generated.resources.text_app_info_package_name
import tweak_alpha.shared.generated.resources.text_app_info_source_dir
import tweak_alpha.shared.generated.resources.text_app_info_target_sdk
import tweak_alpha.shared.generated.resources.text_app_info_version_code
import tweak_alpha.shared.generated.resources.text_app_manager
import tweak_alpha.shared.generated.resources.text_app_manager_description
import tweak_alpha.shared.generated.resources.text_app_search_label
import tweak_alpha.shared.generated.resources.text_app_restore
import tweak_alpha.shared.generated.resources.text_app_uninstall
import tweak_alpha.shared.generated.resources.text_close
import tweak_alpha.shared.generated.resources.text_dialog_cancel
import tweak_alpha.shared.generated.resources.text_dialog_disable_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_enable_disabled_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_force_stop_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_force_stop_confirm
import tweak_alpha.shared.generated.resources.text_dialog_freeze_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_unfreeze_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_restore_system_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_uninstall_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_uninstall_system_app_summary
import tweak_alpha.shared.generated.resources.text_dialog_warm_tip
import tweak_alpha.shared.generated.resources.text_disable_app
import tweak_alpha.shared.generated.resources.text_disabled_apps
import tweak_alpha.shared.generated.resources.text_enable_app
import tweak_alpha.shared.generated.resources.text_enable_disabled_app
import tweak_alpha.shared.generated.resources.text_export_apk
import tweak_alpha.shared.generated.resources.text_extract_apk_overall_progress
import tweak_alpha.shared.generated.resources.text_extract_apk_progress_format
import tweak_alpha.shared.generated.resources.text_extract_apk_run_in_background
import tweak_alpha.shared.generated.resources.text_extract_apk_running
import tweak_alpha.shared.generated.resources.text_extract_apk_task_label
import tweak_alpha.shared.generated.resources.text_extract_apk_task_progress
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_force_kill_app
import tweak_alpha.shared.generated.resources.text_launch
import tweak_alpha.shared.generated.resources.text_no_native_abi
import tweak_alpha.shared.generated.resources.text_selected_apps_format
import tweak_alpha.shared.generated.resources.text_system_apps
import tweak_alpha.shared.generated.resources.text_unable_app
import tweak_alpha.shared.generated.resources.text_unabled_apps
import tweak_alpha.shared.generated.resources.text_uninstalled_system_apps
import tweak_alpha.shared.generated.resources.text_user_apps

internal val AppManagerProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "AppManagerProvider",
            title = Res.string.text_app_manager,
            icon = Res.drawable.ic_module,
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
    var topHeight by remember { mutableStateOf(0.dp) }
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val pages = remember(viewModel) {
        listOf(
            AppPage(
                kind = AppPageKind.System,
                iconRes = Res.drawable.ic_system_apps,
                titleRes = Res.string.text_system_apps,
                listState = viewModel.systemApps,
            ),
            AppPage(
                kind = AppPageKind.User,
                iconRes = Res.drawable.ic_user_apps,
                titleRes = Res.string.text_user_apps,
                listState = viewModel.userApps,
            ),
            AppPage(
                kind = AppPageKind.Frozen,
                iconRes = Res.drawable.ic_ice_app,
                titleRes = Res.string.text_unabled_apps,
                listState = viewModel.unabledApps,
            ),
            AppPage(
                kind = AppPageKind.Disabled,
                iconRes = Res.drawable.ic_disable,
                titleRes = Res.string.text_disabled_apps,
                listState = viewModel.disabledApps,
            ),
            AppPage(
                kind = AppPageKind.Uninstalled,
                iconRes = Res.drawable.ic_history,
                titleRes = Res.string.text_uninstalled_system_apps,
                listState = viewModel.uninstalledSystemApps,
            ),
        )
    }

    val pager = rememberPagerState { pages.size }
    val currentPage by remember { derivedStateOf { pager.currentPage } }
    val currentAppPage = remember(pages, currentPage) { pages[currentPage] }
    val currentPageApps by currentAppPage.listState.collectAsStateWithLifecycle()
    val selectMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val loadingTextRes by viewModel.loadingTextRes.collectAsStateWithLifecycle()
    val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()
    val normalizedSearchQuery = remember(searchQuery) { searchQuery.trim() }
    val currentAppList by rememberFilteredApps(
        apps = currentPageApps,
        query = normalizedSearchQuery,
    )
    val subTitle = if (selectMode) {
        stringResource(Res.string.text_selected_apps_format).format(
            selectedApps.size,
            currentAppList.size
        )
    } else {
        buildString {
            append(stringResource(currentAppPage.titleRes))
            append(": ")
            append("${currentAppList.size} Apps")
        }
    }

    BackHandler(selectMode || searchMode) {
        if (selectMode) {
            viewModel.setSelectedMode(false)
            return@BackHandler
        }
        viewModel.setSearchMode(false)
    }

    AppManagerSelectionEffect(
        selectMode = selectMode,
        onExitSelection = viewModel::cleanSelectedPackageNames,
    )
    AppManagerLoadStateEffects(viewModel)
    AppManagerExtractEffects()
    AppManagerResumeSyncEffect(
        lifecycleOwner = lifecycleOwner,
        onResume = viewModel::syncAppsOnResume,
    )
    AppManagerLoadingDialog(
        loadingState = loadingState,
        loadingTextRes = loadingTextRes,
    )
    ExtractApkProgressDialog()

    ScreenSurface {
        Scaffold(
            topBar = {
                AppManagerTopBar(
                    viewModel = viewModel,
                    title = stringResource(Res.string.text_app_manager),
                    subTitle = subTitle,
                    selectMode = selectMode,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    onNavigationClick = {
                        if (!selectMode && !searchMode) {
                            navigator.goBack()
                        } else if (selectMode) {
                            viewModel.setSelectedMode(false)
                        } else {
                            viewModel.setSearchMode(false)
                        }
                    },
                ) {
                    topHeight = it.height
                }
            },
            floatingToolbarPosition = ToolbarPosition.BottomCenter,
            containerColor = MiuixTheme.colorScheme.surface,
        ) { paddingValues ->

            val listPaddingValues =
                remember(bottomToolBarHeight, paddingValues, direction, topHeight) {
                    PaddingValues(
                        start = paddingValues.calculateStartPadding(direction),
                        end = paddingValues.calculateEndPadding(direction),
                        top = topHeight + 12.dp,
                        bottom = bottomToolBarHeight,
                    )
                }

            AppManagerContentLayout(
                backdrop = backdrop,
                pages = pages,
                pagerState = pager,
                currentPage = currentPage,
                selectMode = selectMode,
                searchQuery = normalizedSearchQuery,
                listPaddingValues = listPaddingValues,
                scrollBehavior = scrollBehavior,
                direction = direction,
                viewModel = viewModel,
            ) {
                bottomToolBarHeight = it
            }
        }
    }
}

@Composable
private fun AppManagerSelectionEffect(
    selectMode: Boolean,
    onExitSelection: () -> Unit,
) {
    LaunchedEffect(selectMode) {
        if (!selectMode) {
            onExitSelection()
        }
    }
}

@Composable
private fun AppManagerLoadStateEffects(viewModel: AppManagerViewModel) {
    val hostState = LocalSnackBarHostState.current

    viewModel.LoadStateLaunchEffect {
        WatchSnackBarState("forceKillSelectedApps", hostState)
        WatchSnackBarState("forceKillApp", hostState)
        WatchSnackBarState("unableSelectedApps", hostState)
        WatchSnackBarState("unableApp", hostState)
        WatchSnackBarState("disableSelectedApps", hostState)
        WatchSnackBarState("disableApp", hostState)
        WatchSnackBarState("enableSelectedApps", hostState)
        WatchSnackBarState("enableApp", hostState)
        WatchSnackBarState("uninstallSelectedApps", hostState)
        WatchSnackBarState("uninstallApp", hostState)
        WatchSnackBarState("restoreSelectedSystemApps", hostState)
        WatchSnackBarState("restoreSystemApp", hostState)
        WatchSnackBarState("extractApk", hostState)
    }
}

@Composable
private fun AppManagerExtractEffects() {
    val hostState = LocalSnackBarHostState.current
    val extractState by ExtractApkSession.state.collectAsStateWithLifecycle()

    LaunchedEffect(extractState.snackbarMessage) {
        val message =
            extractState.snackbarMessage?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        hostState.showSnackbar(message)
        ExtractApkSession.consumeSnackbarMessage()
    }
}

@Composable
private fun BaseViewModel.LoadStateWatcher.WatchSnackBarState(
    id: String,
    hostState: SnackbarHostState,
) {
    Watch(id) {
        it.message?.takeIf(String::isNotBlank)?.also { msg ->
            hostState.showSnackbar(msg)
        }
    }
}

@Composable
private fun AppManagerResumeSyncEffect(
    lifecycleOwner: LifecycleOwner,
    onResume: () -> Unit,
) {
    DisposableEffect(lifecycleOwner, onResume) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun AppManagerLoadingDialog(
    loadingState: Boolean,
    loadingTextRes: StringResource,
) {
    WindowDialog(
        show = loadingState,
        enableWindowDim = true,
        onDismissRequest = {},
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
}

@Composable
private fun ExtractApkProgressDialog() {
    val extractState by ExtractApkSession.state.collectAsStateWithLifecycle()
    val totalCount = extractState.totalCount.coerceAtLeast(1)
    val currentIndex = (extractState.currentIndex + 1).coerceIn(1, totalCount)
    val showDialog = extractState.showDialog

    WindowDialog(
        show = showDialog,
        enableWindowDim = true,
        onDismissRequest = null,
    ) {
        // 后注册同系 handler，吞掉预测返回，避免弹窗被手势动画带偏/关闭
        val blockBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = blockBackState,
            isBackEnabled = showDialog,
            onBackCompleted = { },
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.text_extract_apk_running),
                style = MiuixTheme.textStyles.main,
            )
            Text(
                text = stringResource(
                    Res.string.text_extract_apk_task_label,
                    extractState.currentAppName.ifBlank { "--" },
                    currentIndex,
                    totalCount,
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(
                        Res.string.text_extract_apk_task_progress,
                        extractState.taskProgress,
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                LinearProgressIndicator(
                    progress = extractState.taskProgress / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(
                        Res.string.text_extract_apk_overall_progress,
                        extractState.overallProgress,
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                LinearProgressIndicator(
                    progress = extractState.overallProgress / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = extractState.message.ifBlank {
                    stringResource(
                        Res.string.text_extract_apk_progress_format,
                        extractState.taskProgress,
                        extractState.currentFile.ifBlank { "--" },
                    )
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = ExtractApkSession::dismissDialog,
            ) {
                Text(text = stringResource(Res.string.text_extract_apk_run_in_background))
            }
        }
    }
}

@Composable
private fun AppManagerTopBar(
    viewModel: AppManagerViewModel,
    title: String,
    subTitle: String,
    selectMode: Boolean,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop,
    onNavigationClick: () -> Unit,
    onSizeChanged: (DpSize) -> Unit = {},
) {
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()

    SmallTopAppBar(
        title = title,
        subTitle = subTitle,
        scrollBehavior = scrollBehavior,
        backdrop = backdrop,
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = if (selectMode) MiuixIcons.Close else MiuixIcons.Back,
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (!searchMode) {
                IconButton(
                    onClick = {
                        viewModel.setSearchMode(true)
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = null,
                    )
                }
            }

            if (selectMode) {
                AppManagerSelectionAction(viewModel)
            }
        },
        expander = {
            AnimatedVisibility(
                visible = searchMode
            ) {
                SearchContent(viewModel)
            }
        },
        onSizeChanged = onSizeChanged
    )
}

@Composable
private fun SearchContent(viewModel: AppManagerViewModel) {
    val searchText by viewModel.searchQuery.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SearchBar(
        inputField = {
            InputField(
                query = searchText,
                onQueryChange = viewModel::setSearchQuery,
                onSearch = {},
                expanded = expanded,
                onExpandedChange = { expanded = it },
                label = stringResource(Res.string.text_app_search_label),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {}
}

@Composable
private fun AppManagerSelectionAction(viewModel: AppManagerViewModel) {
    val hasSelectedAll by viewModel.hasSelectedAllPackageNames.collectAsStateWithLifecycle()
    val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()
    val parentState = when {
        selectedApps.isEmpty() -> ToggleableState.Off
        hasSelectedAll -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Block {
        IconButton(onClick = viewModel::toggleSelectedAllApps) {
            Checkbox(
                state = parentState,
                onClick = null
            )
        }
    }
}

@Composable
private fun AppManagerNavBar(
    backdrop: LayerBackdrop,
    pages: List<AppPage>,
    pagerState: PagerState,
    onToolBarHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val background = MiuixTheme.colorScheme.surface
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }

    BottomNavigationBar(
        modifier = Modifier.onSizeChanged {
            onToolBarHeight(with(density) { it.height.toDp() })
        }
            .glassBlur(backdrop)
            .background(
                if (advancedBackdropEffectSupported) {
                    MiuixTheme.colorScheme.surfaceContainer.copy(.5f)
                } else {
                    background
                }
            )
    ) {
        pages.onEachIndexed { index, page ->
            BottomNavigationBarItem(
                currentPage = pagerState.currentPage,
                index = index,
                iconPainter = painterResource(page.iconRes),
                title = stringResource(page.titleRes),
                onTabSelected = {
                    scope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                },
            )
        }
    }
}

@Composable
private fun AppManagerContentLayout(
    backdrop: LayerBackdrop,
    pages: List<AppPage>,
    pagerState: PagerState,
    currentPage: Int,
    selectMode: Boolean,
    searchQuery: String,
    listPaddingValues: PaddingValues,
    scrollBehavior: ScrollBehavior,
    direction: LayoutDirection,
    viewModel: AppManagerViewModel,
    onToolBarHeight: (Dp) -> Unit,
) {
    val targetAppInfo by viewModel.targetAppInfo.collectAsStateWithLifecycle()

    Box {
        HorizontalPager(
            modifier = Modifier.fillMaxSize()
                .layerBackdrop(backdrop),
            state = pagerState,
            userScrollEnabled = !selectMode,
        ) { pageIndex ->
            val page = remember(pageIndex) { pages[pageIndex] }

            AppItems(
                page = page,
                paddingValues = listPaddingValues,
                scrollBehavior = scrollBehavior,
                direction = direction,
                searchQuery = searchQuery,
                selectModeState = viewModel.selectedMode,
                selectedAppsState = viewModel.selectedApps,
                onTap = { appInfo ->
                    if (!selectMode) {
                        viewModel.setTargetAppInfo(appInfo)
                    } else {
                        viewModel.toggleSelectedAppInfo(appInfo.packageName)
                    }
                },
                onLongClick = { appInfo, selectablePackages ->
                    if (!selectMode) {
                        viewModel.setSelectedMode(true)
                        viewModel.setSelectableAppPackageNames(selectablePackages)
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
        ) { visible ->
            if (visible) {
                BottomToolbar(
                    backdrop = backdrop,
                    viewModel = viewModel,
                    pageKind = pages.getOrNull(currentPage)?.kind ?: AppPageKind.System,
                    onToolBarHeight = onToolBarHeight,
                )
            }
        }

        AnimatedContent(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth(),
            targetState = !selectMode,
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
        ) { visible ->
            if (visible) {
                AppManagerNavBar(
                    backdrop = backdrop,
                    pages = pages,
                    pagerState = pagerState,
                    onToolBarHeight = onToolBarHeight,
                )
            }
        }
    }

    AppInfoDialog(viewModel, targetAppInfo)
}

@Composable
private fun BoxScope.BottomToolbar(
    backdrop: LayerBackdrop,
    viewModel: AppManagerViewModel,
    pageKind: AppPageKind,
    onToolBarHeight: (Dp) -> Unit,
) {
    val background = MiuixTheme.colorScheme.surface
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }
    val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()
    val isEmpty = remember(selectedApps) { selectedApps.isEmpty() }
    val isUnfreezeAction = pageKind == AppPageKind.Frozen
    val isEnableDisabledAction = pageKind == AppPageKind.Disabled
    val isRestoreAction = pageKind == AppPageKind.Uninstalled
    val isSystemUninstall = pageKind == AppPageKind.System
    var forceDialogState by remember { mutableStateOf(false) }
    var iceDialogState by remember { mutableStateOf(false) }
    var disableDialogState by remember { mutableStateOf(false) }
    var uninstallDialogState by remember { mutableStateOf(false) }
    var restoreDialogState by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    ForceStopDialog(
        show = forceDialogState,
        onDismissRequest = {
            forceDialogState = false
        },
        onConfirm = {
            viewModel.forceKillSelectedApps()
            forceDialogState = false
        }
    )

    FreezeActionDialog(
        show = iceDialogState,
        isUnfreezeAction = isUnfreezeAction,
        onDismissRequest = {
            iceDialogState = false
        },
        onConfirm = {
            if (isUnfreezeAction) {
                viewModel.enableSelectedApps()
            } else {
                viewModel.unableSelectedApps()
            }
            iceDialogState = false
        }
    )

    DisableActionDialog(
        show = disableDialogState,
        isEnableAction = isEnableDisabledAction,
        onDismissRequest = {
            disableDialogState = false
        },
        onConfirm = {
            if (isEnableDisabledAction) {
                viewModel.enableSelectedApps()
            } else {
                viewModel.disableSelectedApps()
            }
            disableDialogState = false
        }
    )

    UninstallDialog(
        show = uninstallDialogState,
        isSystemUninstall = isSystemUninstall,
        onDismissRequest = {
            uninstallDialogState = false
        },
        onConfirm = {
            viewModel.uninstallSelectedApps()
            uninstallDialogState = false
        }
    )

    RestoreSystemAppDialog(
        show = restoreDialogState,
        onDismissRequest = {
            restoreDialogState = false
        },
        onConfirm = {
            viewModel.restoreSelectedSystemApps()
            restoreDialogState = false
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter)
            .glassBlur(backdrop)
            .background(
                if (advancedBackdropEffectSupported) {
                    MiuixTheme.colorScheme.surfaceContainer.copy(.5f)
                } else {
                    background
                }
            )
            .onSizeChanged {
                onToolBarHeight(with(density) { it.height.toDp() })
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(NavigationBarHeight)
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = {}
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            if (!isRestoreAction) {
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
                        painter = if (isUnfreezeAction) {
                            painterResource(Res.drawable.ic_sun)
                        } else {
                            painterResource(Res.drawable.ic_unable_app)
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                    )
                    Text(
                        text = if (isUnfreezeAction) {
                            stringResource(Res.string.text_enable_app)
                        } else {
                            stringResource(Res.string.text_unable_app)
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface.copy(.75f)
                    )
                }
            }

            // 禁用
            Block {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .weight(1f)
                        .alpha(if (isEmpty) .31f else 1f)
                        .clickable(
                            enabled = !isEmpty,
                        ) {
                            disableDialogState = true
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = if (isEnableDisabledAction) {
                            painterResource(Res.drawable.ic_sun)
                        } else {
                            painterResource(Res.drawable.ic_disable)
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                    )
                    Text(
                        text = if (isEnableDisabledAction) {
                            stringResource(Res.string.text_enable_disabled_app)
                        } else {
                            stringResource(Res.string.text_disable_app)
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface.copy(.75f)
                    )
                }
            }
            }

            // 卸载 / 恢复
            Block {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .weight(1f)
                        .alpha(if (isEmpty) .31f else 1f)
                        .clickable(
                            enabled = !isEmpty,
                        ) {
                            if (isRestoreAction) {
                                restoreDialogState = true
                            } else {
                                uninstallDialogState = true
                            }
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isRestoreAction) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_history),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                        )
                    }
                    Text(
                        text = if (isRestoreAction) {
                            stringResource(Res.string.text_app_restore)
                        } else {
                            stringResource(Res.string.text_app_uninstall)
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface.copy(.75f)
                    )
                }
            }

            // 提取安装包
            Block {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .weight(1f)
                        .alpha(if (isEmpty) .31f else 1f)
                        .clickable(
                            enabled = !isEmpty,
                        ) {
                            viewModel.extractSelectedApps()
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = MiuixIcons.MoveFile,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                    )
                    Text(
                        text = stringResource(Res.string.text_export_apk),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface.copy(.75f)
                    )
                }
            }
        }
    }


}

@Composable
private fun ForceStopDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = stringResource(Res.string.text_dialog_force_stop_app_summary),
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        DialogActionButtons(
            confirmText = stringResource(Res.string.text_dialog_force_stop_confirm),
            onConfirm = onConfirm,
            onCancel = onDismissRequest,
        )
    }
}

@Composable
private fun FreezeActionDialog(
    show: Boolean,
    isUnfreezeAction: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = if (isUnfreezeAction) {
            stringResource(Res.string.text_dialog_unfreeze_app_summary)
        } else {
            stringResource(Res.string.text_dialog_freeze_app_summary)
        },
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        DialogActionButtons(
            confirmText = if (isUnfreezeAction) {
                stringResource(Res.string.text_enable_app)
            } else {
                stringResource(Res.string.text_unable_app)
            },
            onConfirm = onConfirm,
            onCancel = onDismissRequest,
        )
    }
}

@Composable
private fun DisableActionDialog(
    show: Boolean,
    isEnableAction: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = if (isEnableAction) {
            stringResource(Res.string.text_dialog_enable_disabled_app_summary)
        } else {
            stringResource(Res.string.text_dialog_disable_app_summary)
        },
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        DialogActionButtons(
            confirmText = if (isEnableAction) {
                stringResource(Res.string.text_enable_disabled_app)
            } else {
                stringResource(Res.string.text_disable_app)
            },
            onConfirm = onConfirm,
            onCancel = onDismissRequest,
        )
    }
}

@Composable
private fun UninstallDialog(
    show: Boolean,
    isSystemUninstall: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = if (isSystemUninstall) {
            stringResource(Res.string.text_dialog_uninstall_system_app_summary)
        } else {
            stringResource(Res.string.text_dialog_uninstall_app_summary)
        },
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        DialogActionButtons(
            confirmText = stringResource(Res.string.text_app_uninstall),
            onConfirm = onConfirm,
            onCancel = onDismissRequest,
        )
    }
}

@Composable
private fun RestoreSystemAppDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = stringResource(Res.string.text_dialog_restore_system_app_summary),
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        DialogActionButtons(
            confirmText = stringResource(Res.string.text_app_restore),
            onConfirm = onConfirm,
            onCancel = onDismissRequest,
        )
    }
}

@Composable
private fun DialogActionButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(text = confirmText)
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = onCancel,
        ) {
            Text(text = stringResource(Res.string.text_dialog_cancel))
        }
    }
}

@Immutable
private data class AppPage(
    val kind: AppPageKind,
    val iconRes: DrawableResource,
    val titleRes: StringResource,
    val listState: StateFlow<List<AppInfo>>,
)

private enum class AppPageKind {
    System,
    User,
    Frozen,
    Disabled,
    Uninstalled,
}

@Composable
private fun AppItems(
    page: AppPage,
    paddingValues: PaddingValues,
    scrollBehavior: ScrollBehavior,
    direction: LayoutDirection,
    searchQuery: String,
    selectModeState: StateFlow<Boolean>,
    selectedAppsState: StateFlow<Set<String>>,
    onTap: (AppInfo) -> Unit,
    onLongClick: (AppInfo, Set<String>) -> Unit,
) {
    val apps by page.listState.collectAsStateWithLifecycle()
    val selectMode by selectModeState.collectAsStateWithLifecycle()
    val selectedApps by selectedAppsState.collectAsStateWithLifecycle()
    val currentAppList by rememberFilteredApps(
        apps = apps,
        query = searchQuery,
    )
    val selectablePackageNames = remember(currentAppList) {
        currentAppList.map { it.packageName }.toSet()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = paddingValues.calculateStartPadding(direction) + 16.dp,
            top = paddingValues.calculateTopPadding(),
            end = paddingValues.calculateEndPadding(direction) + 16.dp,
            bottom = paddingValues.calculateBottomPadding() + 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = currentAppList,
            key = { it.packageName }
        ) { info ->
            AppItem(
                appInfo = info,
                selectMode = selectMode,
                selected = info.packageName in selectedApps,
                onTap = onTap,
                onLongClick = {
                    onLongClick(it, selectablePackageNames)
                },
            )
        }
    }
}

@Composable
private fun rememberFilteredApps(
    apps: List<AppInfo>,
    query: String,
) = produceState(initialValue = apps, apps, query) {
    value = if (query.isBlank()) {
        apps
    } else {
        withContext(Dispatchers.Default) {
            apps.filterByQuery(query)
        }
    }
}

private fun List<AppInfo>.filterByQuery(query: String): List<AppInfo> {
    if (query.isBlank()) return this
    val normalizedQuery = query.lowercase()
    return filter { appInfo ->
        appInfo.appName.contains(normalizedQuery, ignoreCase = true) ||
                appInfo.packageName.contains(normalizedQuery, ignoreCase = true)
    }
}

@Composable
private fun AppItem(
    appInfo: AppInfo,
    selectMode: Boolean,
    selected: Boolean,
    onTap: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    val abiMap = remember(appInfo.abiList) {
        when {
            appInfo.abiList.isEmpty() -> Res.string.text_no_native_abi
            appInfo.abiList.firstOrNull()?.bitSize == 64 -> Res.string.text_abi_64
            else -> Res.string.text_abi_32
        }
    }
    val abiText = stringResource(abiMap)
    val descriptionText = remember(
        appInfo.packageName,
        appInfo.versionName,
        appInfo.versionCode,
        appInfo.targetSdk,
        appInfo.minSdk,
        abiText,
    ) {
        buildString {
            append(appInfo.packageName)
            append('\n')
            append(appInfo.versionName)
            append(" (${appInfo.versionCode})")
            append("\nTarget: ${appInfo.targetSdk}, Min: ${appInfo.minSdk}")
            append(", ")
            append(abiText)
        }
    }

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
                    modifier = Modifier.clip(Rectangle.copy(12.dp))
                        .border(
                            width = 1.dp,
                            shape = Rectangle.copy(12.dp),
                            color = MiuixTheme.colorScheme.onSurface.copy(.1f)
                        )
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
                text = descriptionText,
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

@Composable
private fun AppInfoDialog(viewModel: AppManagerViewModel, appInfo: AppInfo?) {
    var forceDialogState by remember { mutableStateOf(false) }
    var freezeDialogState by remember { mutableStateOf(false) }
    var disableDialogState by remember { mutableStateOf(false) }
    var uninstallDialogState by remember { mutableStateOf(false) }
    var restoreDialogState by remember { mutableStateOf(false) }
    val isUnfreezeAction = appInfo?.state == AppState.FROZEN
    val isEnableDisabledAction = appInfo?.state == AppState.DISABLED
    val isUninstalled = appInfo?.state == AppState.UNINSTALLED
    val isSystemUninstall = appInfo?.isSystemApp == true && !isUninstalled
    val scope = rememberCoroutineScope()

    LaunchedEffect(appInfo) {
        if (appInfo == null) {
            forceDialogState = false
            freezeDialogState = false
            disableDialogState = false
            uninstallDialogState = false
            restoreDialogState = false
        }
    }

    ForceStopDialog(
        show = forceDialogState,
        onDismissRequest = { forceDialogState = false },
        onConfirm = {
            val packageName = appInfo?.packageName ?: return@ForceStopDialog
            forceDialogState = false
            viewModel.forceKillApp(packageName)
        },
    )

    FreezeActionDialog(
        show = freezeDialogState,
        isUnfreezeAction = isUnfreezeAction,
        onDismissRequest = { freezeDialogState = false },
        onConfirm = {
            val info = appInfo ?: return@FreezeActionDialog
            freezeDialogState = false
            viewModel.setTargetAppInfo(null)
            if (isUnfreezeAction) {
                viewModel.enableApp(info.packageName, info.state)
            } else {
                viewModel.freezeApp(info.packageName)
            }
        },
    )

    DisableActionDialog(
        show = disableDialogState,
        isEnableAction = isEnableDisabledAction,
        onDismissRequest = { disableDialogState = false },
        onConfirm = {
            val info = appInfo ?: return@DisableActionDialog
            disableDialogState = false
            viewModel.setTargetAppInfo(null)
            if (isEnableDisabledAction) {
                viewModel.enableApp(info.packageName, info.state)
            } else {
                viewModel.disableApp(info.packageName)
            }
        },
    )

    UninstallDialog(
        show = uninstallDialogState,
        isSystemUninstall = isSystemUninstall,
        onDismissRequest = { uninstallDialogState = false },
        onConfirm = {
            val packageName = appInfo?.packageName ?: return@UninstallDialog
            uninstallDialogState = false
            viewModel.setTargetAppInfo(null)
            viewModel.uninstallApp(packageName)
        },
    )

    RestoreSystemAppDialog(
        show = restoreDialogState,
        onDismissRequest = { restoreDialogState = false },
        onConfirm = {
            val packageName = appInfo?.packageName ?: return@RestoreSystemAppDialog
            restoreDialogState = false
            viewModel.setTargetAppInfo(null)
            viewModel.restoreSystemApp(packageName)
        },
    )

    OverlayDialog(
        show = appInfo != null,
        title = stringResource(Res.string.text_app_detail),
        onDismissRequest = { viewModel.setTargetAppInfo(null) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            appInfo?.also { info ->
                val apkSize by produceState(initialValue = "--", key1 = info.sourceDir) {
                    value = Files.length(info.sourceDir).getOrNull()?.formatMemorySize(" ") ?: "--"
                }

                BasicComponent(
                    modifier = Modifier.fillMaxWidth(),
                    startAction = {
                        AsyncImage(
                            model = info.iconPath,
                            contentDescription = null,
                            modifier = Modifier.clip(Rectangle.copy(12.dp))
                                .border(
                                    width = 1.dp,
                                    shape = Rectangle.copy(12.dp),
                                    color = MiuixTheme.colorScheme.onSurface.copy(.1f)
                                )
                                .size(48.dp),
                        )
                    },
                    endActions = {
                        TooltipBox(
                            text = stringResource(Res.string.text_launch) + info.appName,
                            enabled = info.state == AppState.ENABLED
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        AppsHelper.launch(info.packageName)
                                    }
                                },
                                enabled = info.state == AppState.ENABLED,
                                modifier = Modifier.alpha(if (info.state == AppState.ENABLED) 1f else .21f)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Play,
                                    contentDescription = null,
                                )
                            }
                        }

                        TooltipBox(
                            text = stringResource(Res.string.text_export_apk),
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.extractApk(
                                        packageName = info.packageName,
                                        appName = info.appName,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.MoveFile,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    title = info.appName,
                    summary = info.versionName,
                    insideMargin = PaddingValues()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .weight(1f, false)
                ) {
                    AppInfoDetailColumn(
                        items = listOf(
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_package_name),
                                value = info.packageName,
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_version_code),
                                value = info.versionCode.toString(),
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_apk_size),
                                value = apkSize,
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_target_sdk),
                                value = formatAndroidApiLabel(info.targetSdk),
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_min_sdk),
                                value = formatAndroidApiLabel(info.minSdk),
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_data_dir),
                                value = info.dataDir.ifBlank { "--" },
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_source_dir),
                                value = info.sourceDir.ifBlank { "--" },
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_first_install),
                                value = formatDateTime(
                                    info.firstInstallTime,
                                    "yyyy-MM-dd HH:mm:ss"
                                ),
                            ),
                            AppInfoDetailItem(
                                title = stringResource(Res.string.text_app_info_last_update),
                                value = formatDateTime(info.lastUpdateTime, "yyyy-MM-dd HH:mm:ss"),
                            ),
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isUninstalled) {
                        Button(
                            onClick = { forceDialogState = true },
                            colors = ButtonDefaults.buttonColorsPrimary(
                                color = MiuixTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(Res.string.text_force_kill_app)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isUninstalled) {
                                restoreDialogState = true
                            } else {
                                uninstallDialogState = true
                            }
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(
                            color = if (isUninstalled) {
                                MiuixTheme.colorScheme.primaryContainer
                            } else {
                                MiuixTheme.colorScheme.error.copy(.5f)
                            },
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isUninstalled) {
                                stringResource(Res.string.text_app_restore)
                            } else {
                                stringResource(Res.string.text_app_uninstall)
                            }
                        )
                    }
                }

                if (!isUninstalled) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { freezeDialogState = true },
                            colors = ButtonDefaults.buttonColorsPrimary(
                                color = MiuixTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (info.state == AppState.FROZEN) {
                                    stringResource(Res.string.text_enable_app)
                                } else {
                                    stringResource(Res.string.text_unable_app)
                                }
                            )
                        }

                        Button(
                            onClick = { disableDialogState = true },
                            colors = ButtonDefaults.buttonColorsPrimary(
                                color = MiuixTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (info.state == AppState.DISABLED) {
                                    stringResource(Res.string.text_enable_disabled_app)
                                } else {
                                    stringResource(Res.string.text_disable_app)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.setTargetAppInfo(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.text_close))
                }
            }
        }
    }
}

@Immutable
private data class AppInfoDetailItem(
    val title: String,
    val value: String,
)

@Composable
private fun AppInfoDetailColumn(items: List<AppInfoDetailItem>) {
    val titleStyle = MiuixTheme.textStyles.body2
    val textMeasurer = rememberTextMeasurer()
    val labelWidth = with(LocalDensity.current) {
        remember(items, titleStyle) {
            items.maxOf { item ->
                textMeasurer.measure(
                    text = item.title,
                    style = titleStyle,
                ).size.width.toDp()
            }
        }
    }

    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                AppInfoDetailRow(
                    title = item.title,
                    value = item.value,
                    labelWidth = labelWidth,
                )
            }
        }
    }
}

@Composable
private fun AppInfoDetailRow(
    title: String,
    value: String,
    labelWidth: Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            modifier = Modifier.width(labelWidth),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun formatAndroidApiLabel(sdk: Int): String {
    if (sdk <= 0) return "--"
    return stringResource(
        Res.string.text_android_api_format,
        androidReleaseName(sdk),
        sdk,
    )
}

private fun androidReleaseName(sdk: Int): String = when (sdk) {
    21 -> "5.0"
    22 -> "5.1"
    23 -> "6.0"
    24 -> "7.0"
    25 -> "7.1"
    26 -> "8.0"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> sdk.toString()
}
