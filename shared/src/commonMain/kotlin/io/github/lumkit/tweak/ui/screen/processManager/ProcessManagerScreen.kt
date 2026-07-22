package io.github.lumkit.tweak.ui.screen.processManager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.SmallTopAppBar
import io.github.lumkit.tweak.common.component.glassBlur
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.model.ProcessInfo
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_process
import tweak_alpha.shared.generated.resources.ic_process_linux
import tweak_alpha.shared.generated.resources.text_dialog_cancel
import tweak_alpha.shared.generated.resources.text_dialog_warm_tip
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_process_cgroup
import tweak_alpha.shared.generated.resources.text_process_cmdline
import tweak_alpha.shared.generated.resources.text_process_command
import tweak_alpha.shared.generated.resources.text_process_count_format
import tweak_alpha.shared.generated.resources.text_process_cpu
import tweak_alpha.shared.generated.resources.text_process_cpuset
import tweak_alpha.shared.generated.resources.text_process_detail
import tweak_alpha.shared.generated.resources.text_process_filter_all
import tweak_alpha.shared.generated.resources.text_process_filter_android
import tweak_alpha.shared.generated.resources.text_process_filter_other
import tweak_alpha.shared.generated.resources.text_process_filter_system
import tweak_alpha.shared.generated.resources.text_process_filter_user
import tweak_alpha.shared.generated.resources.text_process_kill
import tweak_alpha.shared.generated.resources.text_process_kill_confirm
import tweak_alpha.shared.generated.resources.text_process_manager
import tweak_alpha.shared.generated.resources.text_process_manager_description
import tweak_alpha.shared.generated.resources.text_process_mem
import tweak_alpha.shared.generated.resources.text_process_oom_adj
import tweak_alpha.shared.generated.resources.text_process_oom_score_adj
import tweak_alpha.shared.generated.resources.text_process_pid
import tweak_alpha.shared.generated.resources.text_process_sort_cpu
import tweak_alpha.shared.generated.resources.text_process_sort_none
import tweak_alpha.shared.generated.resources.text_process_sort_pid
import tweak_alpha.shared.generated.resources.text_process_sort_res
import tweak_alpha.shared.generated.resources.text_process_stop_app
import tweak_alpha.shared.generated.resources.text_process_stop_app_confirm
import tweak_alpha.shared.generated.resources.text_process_swap
import tweak_alpha.shared.generated.resources.text_process_type
import tweak_alpha.shared.generated.resources.text_process_type_des
import tweak_alpha.shared.generated.resources.text_process_unsupported
import tweak_alpha.shared.generated.resources.text_process_user

internal val ProcessManagerProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "ProcessManagerProvider",
            title = Res.string.text_process_manager,
            icon = Res.drawable.ic_process,
            description = Res.string.text_process_manager_description,
            capabilities = setOf(Capability.SHIZUKU_OR_ROOT),
            route = Screen.ProcessManager(),
            defaultState = FeatureState.ENABLED,
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            },
        )

    @Composable
    override fun Content() {
        ProcessManagerContent()
    }
}

@Composable
fun ProcessManagerContent(
    scrollToPackage: String = "",
    scrollToPid: Int = -1,
    viewModel: ProcessManagerViewModel = viewModel { ProcessManagerViewModel() },
) {
    val navigator = LocalNavigator.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val listState = rememberLazyListState()
    val background = MiuixTheme.colorScheme.surface
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }

    val supported by viewModel.supported.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val processes by viewModel.displayProcesses.collectAsStateWithLifecycle()
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val detailLoading by viewModel.detailLoading.collectAsStateWithLifecycle()

    var bottomToolBarHeight by remember { mutableStateOf(0.dp) }
    var topHeight by remember { mutableStateOf(0.dp) }
    var pendingScroll by remember(scrollToPackage, scrollToPid) {
        mutableStateOf(scrollToPackage.isNotBlank() || scrollToPid > 0)
    }
    // 仅首次定位时短暂高亮；定位完成后清空，不持续保留
    var highlightPackage by remember(scrollToPackage, scrollToPid) {
        mutableStateOf(scrollToPackage)
    }
    var highlightPid by remember(scrollToPackage, scrollToPid) {
        mutableIntStateOf(scrollToPid)
    }
    var confirmKillPid by remember { mutableStateOf<Int?>(null) }
    var confirmStopApp by remember { mutableStateOf<ProcessInfo?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startAutoRefresh()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopAutoRefresh()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopAutoRefresh()
        }
    }

    LaunchedEffect(processes, pendingScroll) {
        if (!pendingScroll || processes.isEmpty()) {
            return@LaunchedEffect
        }
        // 定位前切到「全部」，避免目标进程被当前过滤隐藏
        if (viewModel.filterMode.value != ProcessFilterMode.All) {
            viewModel.setFilterMode(ProcessFilterMode.All)
            return@LaunchedEffect
        }
        val index = viewModel.indexOfTarget(processes, scrollToPackage, scrollToPid)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
        // 无论是否找到，都只执行一次，不再保留滚动/高亮目标
        pendingScroll = false
        highlightPackage = ""
        highlightPid = -1
    }

    BackHandler(searchMode) {
        viewModel.setSearchMode(false)
    }

    ProcessDetailDialog(
        process = detail,
        loading = detailLoading,
        onDismiss = viewModel::dismissDetail,
        onKill = { process -> confirmKillPid = process.pid },
        onStopApp = { process -> confirmStopApp = process },
    )

    ConfirmActionDialog(
        show = confirmKillPid != null,
        summary = stringResource(Res.string.text_process_kill_confirm),
        confirmText = stringResource(Res.string.text_process_kill),
        onDismiss = { confirmKillPid = null },
        onConfirm = {
            val pid = confirmKillPid
            confirmKillPid = null
            if (pid != null) {
                viewModel.killProcess(pid)
            }
        },
    )

    ConfirmActionDialog(
        show = confirmStopApp != null,
        summary = stringResource(Res.string.text_process_stop_app_confirm),
        confirmText = stringResource(Res.string.text_process_stop_app),
        onDismiss = { confirmStopApp = null },
        onConfirm = {
            val process = confirmStopApp
            confirmStopApp = null
            if (process != null) {
                viewModel.killApp(process)
            }
        },
    )

    ScreenSurface {
        Scaffold(
            topBar = {
                ProcessManagerTopBar(
                    title = stringResource(Res.string.text_process_manager),
                    subTitle = stringResource(Res.string.text_process_count_format)
                        .format(processes.size),
                    searchMode = searchMode,
                    searchQuery = searchQuery,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    onNavigationClick = {
                        if (searchMode) {
                            viewModel.setSearchMode(false)
                        } else {
                            navigator.goBack()
                        }
                    },
                    onSearchClick = { viewModel.setSearchMode(true) },
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSizeChanged = { topHeight = it.height },
                    sortMode = sortMode,
                    onSortChange = viewModel::setSortMode
                )
            },
            floatingToolbarPosition = ToolbarPosition.BottomCenter,
            containerColor = MiuixTheme.colorScheme.surface,
        ) { padding ->
            val contentPadding = remember(padding, bottomToolBarHeight, direction, topHeight) {
                PaddingValues(
                    start = padding.calculateLeftPadding(direction) + 16.dp,
                    end = padding.calculateRightPadding(direction) + 16.dp,
                    top = topHeight + 12.dp,
                    bottom = padding.calculateBottomPadding() +
                            bottomToolBarHeight + 16.dp,
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                when {
                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator()
                        }
                    }

                    !supported -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.text_process_unsupported),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                                .layerBackdrop(backdrop = backdrop),
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 不使用稳定 key：刷新重排时按 index+offset 固定视口，
                            // 避免 LazyList 跟着上次可见 Item 滚动。
                            itemsIndexed(items = processes) { _, process ->
                                ProcessListItem(
                                    process = process,
                                    highlighted = (highlightPid > 0 && process.pid == highlightPid) ||
                                            (highlightPackage.isNotBlank() &&
                                                    process.appPackageName == highlightPackage),
                                    onClick = { viewModel.openDetail(process) },
                                )
                            }
                        }

                        ProcessFilterToolbar(
                            filterMode = filterMode,
                            onFilterChange = viewModel::setFilterMode,
                            background = background,
                            advancedBackdropEffectSupported = advancedBackdropEffectSupported,
                            backdrop = backdrop,
                            onHeight = { bottomToolBarHeight = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessManagerTopBar(
    title: String,
    subTitle: String,
    searchMode: Boolean,
    searchQuery: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop,
    onNavigationClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSizeChanged: (DpSize) -> Unit = {},
    sortMode: ProcessSortMode,
    onSortChange: (ProcessSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SmallTopAppBar(
        title = title,
        subTitle = subTitle,
        scrollBehavior = scrollBehavior,
        backdrop = backdrop,
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (!searchMode) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = null,
                    )
                }
            }
        },
        expander = {
            val sortTabs = listOf(
                stringResource(Res.string.text_process_sort_cpu),
                stringResource(Res.string.text_process_sort_res),
                stringResource(Res.string.text_process_sort_pid),
                stringResource(Res.string.text_process_sort_none),
            )
            val selectedSortIndex = when (sortMode) {
                ProcessSortMode.Cpu -> 0
                ProcessSortMode.Res -> 1
                ProcessSortMode.Pid -> 2
                ProcessSortMode.None -> 3
            }
            TabRowWithContour(
                tabs = sortTabs,
                selectedTabIndex = selectedSortIndex,
                onTabSelected = { index ->
                    onSortChange(
                        when (index) {
                            0 -> ProcessSortMode.Cpu
                            1 -> ProcessSortMode.Res
                            2 -> ProcessSortMode.Pid
                            else -> ProcessSortMode.None
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            )

            AnimatedVisibility(visible = searchMode) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearch = {},
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                        )
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {}
            }
        },
        onSizeChanged = onSizeChanged,
    )
}

@Composable
private fun ProcessFilterToolbar(
    filterMode: ProcessFilterMode,
    background: Color,
    backdrop: LayerBackdrop,
    advancedBackdropEffectSupported: Boolean,
    onFilterChange: (ProcessFilterMode) -> Unit,
    onHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val filterTabs = listOf(
        stringResource(Res.string.text_process_filter_user),
        stringResource(Res.string.text_process_filter_system),
        stringResource(Res.string.text_process_filter_android),
        stringResource(Res.string.text_process_filter_other),
        stringResource(Res.string.text_process_filter_all),
    )
    val selectedFilterIndex = when (filterMode) {
        ProcessFilterMode.AndroidUser -> 0
        ProcessFilterMode.AndroidSystem -> 1
        ProcessFilterMode.Android -> 2
        ProcessFilterMode.Other -> 3
        ProcessFilterMode.All -> 4
    }

    Row(
        modifier = Modifier.fillMaxWidth().glassBlur(backdrop)
            .background(
                if (advancedBackdropEffectSupported) {
                    MiuixTheme.colorScheme.surfaceContainer.copy(.5f)
                } else {
                    background
                }
            ).onSizeChanged {
                onHeight(with(density) { it.height.toDp() })
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            OverlayDropdownPreference(
                modifier = Modifier.fillMaxWidth(),
                items = filterTabs,
                selectedIndex = selectedFilterIndex,
                title = stringResource(Res.string.text_process_type),
                summary = stringResource(Res.string.text_process_type_des),
                onSelectedIndexChange = {
                    onFilterChange(ProcessFilterMode.entries.getOrNull(it) ?: ProcessFilterMode.All)
                }
            )
        }
    }
}

@Composable
private fun ProcessListItem(
    process: ProcessInfo,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val container = if (highlighted) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val summary = remember(process) {
        buildString {
            append(process.name)
            append('\n')
            append("PID ")
            append(process.pid)
            append(" · ")
            append("%.1f%%".format(process.cpu))
            append(" · ")
            append(formatProcessMem(process.res))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Rectangle.copy(cornerRadius = 16.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            startAction = {
                AsyncImage(
                    model = process.iconPath.takeIf { it.isNotBlank() },
                    contentDescription = null,
                    error = painterResource(Res.drawable.ic_process_linux),
                    modifier = Modifier
                        .clip(Rectangle.copy(12.dp))
                        .border(
                            width = 1.dp,
                            shape = Rectangle.copy(12.dp),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        )
                        .size(48.dp),
                )
            },
            endActions = {
                Text(
                    text = "%.1f%%".format(process.cpu),
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    style = MiuixTheme.textStyles.body2,
                )
            },
        ) {
            Text(
                text = process.displayName,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.31f),
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                ),
            )
        }
    }
}

@Composable
private fun ProcessDetailDialog(
    process: ProcessInfo?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onKill: (ProcessInfo) -> Unit,
    onStopApp: (ProcessInfo) -> Unit,
) {
    OverlayDialog(
        title = stringResource(Res.string.text_process_detail),
        summary = process?.displayName.orEmpty(),
        show = process != null,
        onDismissRequest = onDismiss,
    ) {
        val info = process ?: return@OverlayDialog
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailRow(stringResource(Res.string.text_process_pid), info.pid.toString())
                        DetailRow(stringResource(Res.string.text_process_cpu), "%.1f%%".format(info.cpu))
                        DetailRow(stringResource(Res.string.text_process_mem), formatProcessMem(info.res))
                        DetailRow(stringResource(Res.string.text_process_swap), formatProcessMem(info.swap))
                        DetailRow(stringResource(Res.string.text_process_user), info.user)
                        DetailRow(stringResource(Res.string.text_process_command), info.command)
                        DetailRow(stringResource(Res.string.text_process_cmdline), info.cmdline)
                        DetailRow(stringResource(Res.string.text_process_cpuset), info.cpuSet)
                        DetailRow(stringResource(Res.string.text_process_oom_adj), info.oomAdj)
                        DetailRow(stringResource(Res.string.text_process_oom_score_adj), info.oomScoreAdj)
                        DetailRow(stringResource(Res.string.text_process_cgroup), info.cGroup)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { onKill(info) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(text = stringResource(Res.string.text_process_kill))
                        }
                        if (info.isAndroidProcess) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onStopApp(info) },
                            ) {
                                Text(text = stringResource(Res.string.text_process_stop_app))
                            }
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDismiss,
                    ) {
                        Text(text = stringResource(Res.string.text_dialog_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    show: Boolean,
    summary: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = stringResource(Res.string.text_dialog_warm_tip),
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = confirmText)
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(Res.string.text_dialog_cancel))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            style = MiuixTheme.textStyles.footnote2,
        )
        Text(
            text = value.ifBlank { "-" },
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

private fun formatProcessMem(kb: Long): String {
    return if (kb > 8192L) {
        "${kb / 1024L}MB"
    } else {
        "${kb}KB"
    }
}
