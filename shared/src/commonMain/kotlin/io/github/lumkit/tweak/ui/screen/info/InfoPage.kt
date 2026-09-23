package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.component.CategoryCard
import io.github.lumkit.tweak.common.component.ChartState
import io.github.lumkit.tweak.common.component.LintStackChart
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.component.rememberChartState
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.isInfoPageSectionVisible
import io.github.lumkit.tweak.common.utils.animatedColorAsBattery
import io.github.lumkit.tweak.common.utils.animatedColorAsUsed
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.common.utils.rememberRequestOverlayPermission
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.overlay.OverlayMonitor
import io.github.lumkit.tweak.ui.screen.fpsRecord.showRecordOverlay
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.RichTooltip
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_process_linux
import tweak_alpha.shared.generated.resources.nav_home
import tweak_alpha.shared.generated.resources.text_battery
import tweak_alpha.shared.generated.resources.text_cpu_state
import tweak_alpha.shared.generated.resources.text_fps_record_overlay
import tweak_alpha.shared.generated.resources.text_fps_record_overlay_description
import tweak_alpha.shared.generated.resources.text_gpu_state
import tweak_alpha.shared.generated.resources.text_info_available
import tweak_alpha.shared.generated.resources.text_info_metric_load
import tweak_alpha.shared.generated.resources.text_info_metric_temperature
import tweak_alpha.shared.generated.resources.text_info_used_of_total
import tweak_alpha.shared.generated.resources.text_label_flash
import tweak_alpha.shared.generated.resources.text_label_free
import tweak_alpha.shared.generated.resources.text_label_total
import tweak_alpha.shared.generated.resources.text_label_used
import tweak_alpha.shared.generated.resources.text_label_user
import tweak_alpha.shared.generated.resources.text_memory_physical
import tweak_alpha.shared.generated.resources.text_memory_state
import tweak_alpha.shared.generated.resources.text_mini_load_overlay
import tweak_alpha.shared.generated.resources.text_mini_load_overlay_description
import tweak_alpha.shared.generated.resources.text_overlay_load
import tweak_alpha.shared.generated.resources.text_overlay_load_description
import tweak_alpha.shared.generated.resources.text_overlay_thread
import tweak_alpha.shared.generated.resources.text_overlay_thread_description
import tweak_alpha.shared.generated.resources.text_overlay_watcher
import tweak_alpha.shared.generated.resources.text_overlay_watcher_description
import tweak_alpha.shared.generated.resources.text_reboot
import tweak_alpha.shared.generated.resources.text_reboot_to_bl
import tweak_alpha.shared.generated.resources.text_reboot_to_edl
import tweak_alpha.shared.generated.resources.text_reboot_to_rec
import tweak_alpha.shared.generated.resources.text_shutdown
import tweak_alpha.shared.generated.resources.text_storage
import tweak_alpha.shared.generated.resources.text_storage_flash_type
import tweak_alpha.shared.generated.resources.text_storage_free
import tweak_alpha.shared.generated.resources.text_storage_total
import tweak_alpha.shared.generated.resources.text_swap
import tweak_alpha.shared.generated.resources.text_total_memory
import tweak_alpha.shared.generated.resources.text_total_memory_used
import tweak_alpha.shared.generated.resources.text_used_load

private enum class InfoPageSection(val key: String) {
    Cpu("cpu"),
    Memory("memory"),
    Gpu("gpu"),
    More("more"),
    ;

    companion object {
        fun fromKey(key: String): InfoPageSection? = entries.find { it.key == key }
    }
}

@Composable
fun InfoPage() {
    val loadState by DeviceInfoViewModel.loadingState.collectAsStateWithLifecycle()
    val sectionOrder by DeviceInfoViewModel.sectionOrder.collectAsStateWithLifecycle()
    val enabledCards by DeviceInfoViewModel.enabledCards.collectAsStateWithLifecycle()
    val visibleSections = remember(sectionOrder, enabledCards) {
        sectionOrder.filter { isInfoPageSectionVisible(it, enabledCards) }
    }
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val backdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }
    val navigator = LocalNavigator.current
    val hapticFeedback = LocalHapticFeedback.current
    val overlayAlpha by animateFloatAsState(
        targetValue = if (loadState) 0f else .5f,
        animationSpec = tween(durationMillis = 400)
    )

    val lazyListState = DeviceInfoViewModel.listState
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        DeviceInfoViewModel.moveSection(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.nav_home),
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    actions = {
                        Actions()
                    }
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

            val enabledFloatNavBar by GlobalViewModel.enabledFloatNavBar.collectAsStateWithLifecycle()
            val navBarBottomPadding by animateDpAsState(
                targetValue = if (enabledFloatNavBar) {
                    28.dp
                } else {
                    16.dp
                }
            )

            LazyColumn(
                modifier = Modifier.layerBackdrop(backdrop)
                    .padding(padding)
                    .fillMaxSize()
                    .overScrollVertical()
                    .then(
                        if (overlayAlpha > 0f) {
                            Modifier.blur(24.dp * overlayAlpha)
                        } else {
                            Modifier
                        }
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = lazyListState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = it.calculateTopPadding() + 16.dp,
                    bottom = it.calculateBottomPadding() + NavigationBarHeight + navBarBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = visibleSections,
                    key = { it },
                ) { sectionKey ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = sectionKey,
                    ) { isDragging ->
                        val dragAlpha by animateFloatAsState(
                            targetValue = if (isDragging) 0.92f else 1f,
                            animationSpec = tween(durationMillis = 120),
                        )
                        val longPressHub = remember(sectionKey) { InfoItemLongPressHub() }
                        val reorderSlopPx = with(LocalDensity.current) { 28.dp.toPx() }
                        val dragDetector = remember(reorderSlopPx, longPressHub) {
                            LongPressThenSlopDragDetector(reorderSlopPx) {
                                longPressHub.emitShow()
                            }
                        }
                        CompositionLocalProvider(LocalInfoItemLongPress provides longPressHub) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { alpha = dragAlpha }
                                    .draggableHandle(
                                        dragGestureDetector = dragDetector,
                                        onDragStarted = {
                                            longPressHub.emitDismiss()
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate,
                                            )
                                        },
                                        onDragStopped = {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.GestureEnd,
                                            )
                                        },
                                    ),
                            ) {
                                when (InfoPageSection.fromKey(sectionKey)) {
                                    InfoPageSection.Cpu -> CpuInfoContent { vo ->
                                        navigator.navigate(
                                            Screen.ProcessManager(
                                                scrollToPackage = vo.packageName,
                                                scrollToPid = vo.pid,
                                            )
                                        )
                                    }
                                    InfoPageSection.Memory -> MemoryInfoContent()
                                    InfoPageSection.Gpu -> GpuInfoContent()
                                    InfoPageSection.More -> MoreInfoContent()
                                    null -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }

        if (overlayAlpha > 0f) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .then(
                        if (!loadState) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = null,
                            ) {}
                        } else {
                            Modifier
                        }
                    )
                    .alpha(overlayAlpha)
                    .background(
                        color = if (backdropEffectSupported) {
                            Color.Transparent
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun CpuInfoContent(
    onTopProcessTap: (DeviceInfoViewModel.TopProcessVo) -> Unit,
) {
    val cpuState by DeviceInfoViewModel.cpuInfoState.collectAsStateWithLifecycle()
    val topProcessSupport by DeviceInfoViewModel.topProcessSupportState.collectAsStateWithLifecycle()
    val enableProcessInfo by DeviceInfoViewModel.enabledProcessInfo.collectAsStateWithLifecycle()
    val enableProcessInfoState = topProcessSupport && enableProcessInfo
    val chartState = rememberChartState()

    val listener: (DeviceInfoViewModel.CpuInfoModel) -> Unit = remember {
        {
            chartState.push(it.coreLoad ?: 0f)
        }
    }

    DisposableEffect(listener) {
        DeviceInfoViewModel.addCpuInfoUpdateListener(listener)

        onDispose {
            DeviceInfoViewModel.removeCpuInfoUpdateListener(listener)
        }
    }

    val cpuSubtitle = buildList {
        cpuState?.coreLoadText?.takeIf { it.isNotBlank() }?.let { load ->
            add(stringResource(Res.string.text_info_metric_load).format(load))
        }
        cpuState?.coreTemperatureText?.takeIf { it.isNotBlank() }?.let { temp ->
            add(stringResource(Res.string.text_info_metric_temperature).format(temp))
        }
    }.joinToString("     ")

    CategoryCard(
        title = stringResource(Res.string.text_cpu_state),
        subTitle = cpuSubtitle,
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.None,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(
                    if (enableProcessInfoState) {
                        120.dp
                    } else {
                        64.dp
                    }
                )
        ) {
            // 独立订阅进程列表，CPU 采样重组时尽量跳过该子树
            TopProcessPanel(
                visible = enableProcessInfoState,
                onTopProcessTap = onTopProcessTap,
            )

            Box(
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight()
                    .weight(1f),
            ) {
                LintStackChart(
                    modifier = Modifier.fillMaxSize()
                        .alpha(.4f),
                    state = chartState,
                )

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    cpuState?.socName?.takeIf { it.isNotBlank() }?.let { socName ->
                        Text(
                            text = socName,
                            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                    cpuState?.coreCluster?.takeIf { it.isNotBlank() }?.let { cluster ->
                        Text(
                            text = "Cores $cluster",
                            color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        CpuCoreContent()
    }
}

@Composable
private fun RowScope.TopProcessPanel(
    visible: Boolean,
    onTopProcessTap: (DeviceInfoViewModel.TopProcessVo) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.weight(1f)
    ) {
        val topProcessVo by DeviceInfoViewModel.topProcessState.collectAsStateWithLifecycle()
        Row {
            Box(
                modifier = Modifier.fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (topProcessVo.isNotEmpty()) {
                    // 固定高度短列表，避免嵌套 LazyColumn 与外层抢测量
                    Column(modifier = Modifier.fillMaxSize()) {
                        topProcessVo.forEach { process ->
                            key(process.pid) {
                                TopProcessItem(process, onTopProcessTap)
                            }
                        }
                    }
                } else {
                    InfiniteProgressIndicator()
                }
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun TopProcessItem(
    topProcessVo: DeviceInfoViewModel.TopProcessVo,
    onTap: (DeviceInfoViewModel.TopProcessVo) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(Rectangle.copy(cornerRadius = 4.dp))
            .clickable {
                onTap(topProcessVo)
            }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AsyncImage(
            model = topProcessVo.iconPath,
            contentDescription = null,
            error = painterResource(Res.drawable.ic_process_linux),
            modifier = Modifier.clip(Rectangle.copy(4.dp))
                .border(
                    width = .5.dp,
                    shape = Rectangle.copy(4.dp),
                    color = MiuixTheme.colorScheme.onSurface.copy(.1f)
                )
                .size(16.dp)
        )

        Text(
            text = topProcessVo.displayName,
            color = MiuixTheme.colorScheme.onSurface.copy(.75f),
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
            ),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = topProcessVo.cpuText,
            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 8.sp,
                lineHeight = 8.sp,
            ),
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}

@Composable
private fun CpuCoreContent() {
    val cpuState by DeviceInfoViewModel.cpuInfoState.collectAsStateWithLifecycle()
    val cpuStates = cpuState?.cpuStates.orEmpty()
    val columns = (cpuStates.size / 2).coerceAtLeast(1)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cpuStates.forEach { core ->
            // 仅按核心编号保活 chart 状态；sampleId 变化仍会驱动重组
            key(core.number) {
                CpuCoreItem(core)
            }
        }
    }
}

@Composable
private fun FlowRowScope.CpuCoreItem(core: DeviceInfoViewModel.CoreInfoModel) {
    // 核图历史不必像总览那样长，降低每周期重算成本
    val chartState = rememberChartState(
        initialStates = List(24) { ChartState(0f) },
    )

    // 随采样周期重组时推入负载（sampleId 保证每次都重组）
    SideEffect {
        chartState.push(core.load)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .height(75.dp)
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            LintStackChart(
                modifier = Modifier.fillMaxSize()
                    .alpha(.4f),
                state = chartState,
            )

            Text(
                text = core.loadText,
                color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                style = MiuixTheme.textStyles.footnote2,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        core.currentFreq?.takeIf { it.isNotBlank() }?.let { freq ->
            Text(
                text = freq,
                color = MiuixTheme.colorScheme.onSurface.copy(.7f),
                style = MiuixTheme.textStyles.body2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp
                ),
            )
        }

        val minFreq = core.minFreq?.takeIf { it.isNotBlank() }
        val maxFreq = core.maxFreq?.takeIf { it.isNotBlank() }
        if (minFreq != null && maxFreq != null) {
            Text(
                text = "$minFreq~$maxFreq",
                color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp
                ),
            )
        }
    }
}

@Composable
private fun MemoryInfoContent() {
    val memoryState by DeviceInfoViewModel.memoryInfoState.collectAsStateWithLifecycle()

    // 高频采样下不做进度动画，避免 Debug 下动画帧叠加卡顿
    val load = memoryState?.totalUsed ?: 0f
    val memoryLoad = memoryState?.memoryUsed ?: 0f
    val swapLoad = memoryState?.swapUsed ?: 0f

    val loadColor by animatedColorAsUsed(load)
    val memoryLoadColor by animatedColorAsUsed(memoryLoad)
    val swapLoadColor by animatedColorAsUsed(swapLoad)
    val swapLabel = memoryState?.zramCompAlgorithm
        ?.takeIf { it.isNotBlank() }
        ?.let { "${stringResource(Res.string.text_swap)} ($it)" }
        ?: stringResource(Res.string.text_swap)

    CategoryCard(
        title = stringResource(Res.string.text_memory_state)
    ) {
        ValueRichTooltipBox(
            title = stringResource(Res.string.text_memory_state),
            lines = listOf(
                stringResource(Res.string.text_memory_physical) to stringResource(
                    Res.string.text_info_used_of_total,
                ).format(
                    memoryState?.memoryUsedSizeText ?: "N/A",
                    memoryState?.memorySizeUnitText ?: "N/A",
                ),
                stringResource(Res.string.text_info_available) to (memoryState?.memoryAvailableSizeText ?: "N/A"),
                swapLabel to stringResource(
                    Res.string.text_info_used_of_total,
                ).format(
                    memoryState?.swapUsedSizeText ?: "N/A",
                    memoryState?.swapSizeUnitText ?: "N/A",
                ),
                stringResource(Res.string.text_total_memory_used) to stringResource(
                    Res.string.text_info_used_of_total,
                ).format(
                    memoryState?.totalUsedSizeText ?: "N/A",
                    memoryState?.totalSizeUnitText ?: "N/A",
                ),
                "SwapCached" to (memoryState?.swapCacheUnitText ?: "N/A"),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = load,
                        size = 80.dp,
                        strokeWidth = 16.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = loadColor
                        ),
                    )

                    Text(
                        text = stringResource(Res.string.text_total_memory),
                        color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                        style = MiuixTheme.textStyles.body2,
                    )
                }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight()
                    .padding(horizontal = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = memoryLoad,
                            height = 8.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = memoryLoadColor
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MemoryTable(
                            title = stringResource(Res.string.text_memory_physical),
                            content = stringResource(Res.string.text_info_used_of_total).format(
                                memoryState?.memoryUsedSizeText ?: "N/A",
                                memoryState?.memorySizeUnitText ?: "N/A",
                            ),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = swapLoad,
                            height = 8.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = swapLoadColor
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MemoryTable(
                            title = swapLabel,
                            content = stringResource(Res.string.text_info_used_of_total).format(
                                memoryState?.swapUsedSizeText ?: "N/A",
                                memoryState?.swapSizeUnitText ?: "N/A",
                            ),
                        )
                    }
                    // TODO 操作按钮
                }

                Row {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(Res.string.text_total_memory_used))
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = MiuixTheme.colorScheme.onSurface.copy(.31f)
                                )
                            ) {
                                append(
                                    stringResource(Res.string.text_info_used_of_total).format(
                                        memoryState?.totalUsedSizeText ?: "N/A",
                                        memoryState?.totalSizeUnitText ?: "N/A",
                                    )
                                )
                            }
                        },
                        color = MiuixTheme.colorScheme.onSurface.copy(.7f),
                        style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = buildAnnotatedString {
                            append("SwapCached ")
                            withStyle(
                                SpanStyle(
                                    color = MiuixTheme.colorScheme.onSurface.copy(.31f)
                                )
                            ) {
                                append(memoryState?.swapCacheUnitText ?: "N/A")
                            }
                        },
                        color = MiuixTheme.colorScheme.onSurface.copy(.7f),
                        style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun MemoryTable(
    title: String,
    content: String,
) {
    Row {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface.copy(.7f),
            style = MiuixTheme.textStyles.footnote2,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.width(28.dp))

        Text(
            text = content,
            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
            style = MiuixTheme.textStyles.footnote2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ValueRichTooltipBox(
    title: String,
    lines: List<Pair<String, String>>,
    content: @Composable () -> Unit,
) {
    val longPressHub = LocalInfoItemLongPress.current
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val colors = TooltipDefaults.richTooltipColors()

    DisposableEffect(longPressHub, state, scope) {
        val show:  () -> Unit = { scope.launch { state.show() } }
        val dismiss = { state.dismiss() }
        longPressHub.show = show
        longPressHub.dismiss = dismiss
        onDispose {
            if (longPressHub.show === show) {
                longPressHub.show = null
                longPressHub.dismiss = null
            }
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above,
        ),
        tooltip = {
            RichTooltip(
                title = {
                    Text(
                        text = title,
                        color = colors.titleContentColor,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                },
                colors = colors,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                color = colors.contentColor,
                                style = MiuixTheme.textStyles.body2,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = value,
                                color = colors.titleContentColor,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }
        },
        state = state,
        focusable = true,
        enableUserInput = false,
    ) {
        content()
    }
}

@Composable
private fun GpuInfoContent() {
    val density = LocalDensity.current
    val translateY = remember {
        with(density) {
            (-1.5f).dp.toPx()
        }
    }
    val gpuInfoModel by DeviceInfoViewModel.gpuInfoState.collectAsStateWithLifecycle()
    val load = gpuInfoModel?.load
    val loadColor by animatedColorAsUsed(load ?: 0f)
    val gpuSupportedState by DeviceInfoViewModel.gpuSupported.collectAsStateWithLifecycle()

    CategoryCard(
        title = stringResource(Res.string.text_gpu_state)
    ) {
        if (gpuSupportedState) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (load != null) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = load,
                            size = 80.dp,
                            strokeWidth = 16.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = loadColor
                            ),
                        )

                        Text(
                            text = stringResource(Res.string.text_used_load),
                            color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                            style = MiuixTheme.textStyles.body2,
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight()
                            .padding(horizontal = 8.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val currentFreq = gpuInfoModel?.currentFreq
                    val freqRangeText = gpuInfoModel?.freqRangeText
                    if (!currentFreq.isNullOrBlank() || !freqRangeText.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (!currentFreq.isNullOrBlank()) {
                                Text(
                                    text = currentFreq,
                                    color = MiuixTheme.colorScheme.onSurface.copy(.7f),
                                    style = MiuixTheme.textStyles.body1,
                                )
                            }

                            if (!freqRangeText.isNullOrBlank()) {
                                Text(
                                    text = freqRangeText,
                                    color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                                    style = MiuixTheme.textStyles.footnote2,
                                    modifier = Modifier.padding(start = 4f.dp)
                                        .graphicsLayer {
                                            translationY = translateY
                                        },
                                )
                            }
                        }
                    }

                    gpuInfoModel?.loadText?.takeIf { it.isNotBlank() }?.let { loadText ->
                        Text(
                            text = loadText,
                            color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                            style = MiuixTheme.textStyles.footnote2
                                .copy(
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                ),
                        )
                    }

                    gpuInfoModel?.displayInfo?.takeIf { it.isNotBlank() }?.let { displayInfo ->
                        Text(
                            text = displayInfo,
                            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                            style = MiuixTheme.textStyles.footnote2
                                .copy(
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                ),
                        )
                    }
                }
            }
        } else {
            gpuInfoModel?.displayInfo?.takeIf { it.isNotBlank() }?.let { displayInfo ->
                Text(
                    text = displayInfo,
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
        }
    }
}

@Composable
private fun MoreInfoContent() {
    val moreInfoModel by DeviceInfoViewModel.moreInfoState.collectAsStateWithLifecycle()
    val enabledCards by DeviceInfoViewModel.enabledCards.collectAsStateWithLifecycle()
    val showBattery = "battery" in enabledCards
    val showStorage = "storage" in enabledCards

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        if (showBattery) {
            BatteryContent(moreInfoModel?.battery)
        }
        if (showStorage) {
            StorageContent(moreInfoModel?.storage)
        }
    }
}

@Composable
private fun FlowRowScope.BatteryContent(
    batteryModel: DeviceInfoViewModel.BatteryInfoModel?,
) {
    val load = batteryModel?.capacity ?: 0f
    val loadColor by animatedColorAsBattery(load)

    CategoryCard(
        title = stringResource(Res.string.text_battery),
        modifier = Modifier.fillMaxWidth()
            .weight(1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = load,
                    size = 48.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = loadColor
                    ),
                    strokeWidth = 8.dp,
                )
                Text(
                    text = batteryModel?.capacityText.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 10.sp,
                        lineHeight = 10.sp
                    ),
                    color = MiuixTheme.colorScheme.onSurface.copy(.5f)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row {
                    batteryModel?.levelText?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f).defaultMinSize(minWidth = 4.dp))
                    batteryModel?.currentText?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                        )
                    }
                }
                Row {
                    batteryModel?.temperatureText?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f).defaultMinSize(minWidth = 4.dp))
                    batteryModel?.powerText?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRowScope.StorageContent(
    storageModel: DeviceInfoViewModel.StorageInfoModel?,
) {
    val load = storageModel?.usedLoad ?: 0f
    val loadColor by animatedColorAsUsed(load)

    CategoryCard(
        title = stringResource(Res.string.text_storage),
        modifier = Modifier.fillMaxWidth()
            .weight(1f)
    ) {
        ValueRichTooltipBox(
            title = stringResource(Res.string.text_storage),
            lines = buildList {
                add(
                    stringResource(Res.string.text_label_used) to
                        (storageModel?.usedTextInline ?: "N/A"),
                )
                add(
                    stringResource(Res.string.text_label_free) to
                        (storageModel?.freeText ?: "N/A"),
                )
                add(
                    stringResource(Res.string.text_label_total) to
                        (storageModel?.totalText ?: "N/A"),
                )
                add(
                    stringResource(Res.string.text_label_flash) to
                        (storageModel?.flashType ?: "N/A"),
                )
                storageModel?.userSpace?.takeIf { it.isNotBlank() }?.let { space ->
                    add(stringResource(Res.string.text_label_user) to space)
                }
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = load,
                        size = 48.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = loadColor
                        ),
                        strokeWidth = 8.dp,
                    )
                    Text(
                        text = storageModel?.usedText ?: "N/A",
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = 10.sp,
                            lineHeight = 10.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                        textAlign = TextAlign.Center
                    )
                }

            Column {
                Text(
                    text = stringResource(Res.string.text_storage_free)
                        .format(storageModel?.freeText ?: "N/A"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                )
                Text(
                    text = stringResource(Res.string.text_storage_total)
                        .format(storageModel?.totalText ?: "N/A"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                )
                Text(
                    text = stringResource(Res.string.text_storage_flash_type)
                        .format(storageModel?.flashType ?: "N/A"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                )
            }
            }
        }
    }
}

data class RebootAction(
    val text: StringResource,
    val onTap: () -> Unit
)

@Composable
private fun RowScope.Actions() {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    // 监听器悬浮窗
    Box {
        var popState by remember { mutableStateOf(false) }

        val requestOverlay = rememberRequestOverlayPermission {
            popState = true
        }

        IconButton(
            onClick = {
                requestOverlay()
            }
        ) {
            Icon(
                imageVector = MiuixIcons.Backup,
                contentDescription = null
            )
        }

        val loadWatcherShowState by OverlayMonitor.loadWatcherIsShowing.collectAsStateWithLifecycle()
        val threadWatcherShowState by OverlayMonitor.threadWatcherIsShowing.collectAsStateWithLifecycle()
        val miniLoadOverlayShowState by OverlayMonitor.miniLoadWatcherIsShowing.collectAsStateWithLifecycle()

        OverlayDialog(
            title = stringResource(Res.string.text_overlay_watcher),
            summary = stringResource(Res.string.text_overlay_watcher_description),
            show = popState,
            onDismissRequest = { popState = false }
        ) {
            Column {
                SwitchPreference(
                    title = stringResource(Res.string.text_overlay_load),
                    summary = stringResource(Res.string.text_overlay_load_description),
                    checked = loadWatcherShowState,
                    onCheckedChange = {
                        if (!it) {
                            OverlayMonitor.hideLoadWatcherOverlay()
                        } else {
                            OverlayMonitor.showLoadWatcherOverlay()
                        }
                    }
                )

                SwitchPreference(
                    title = stringResource(Res.string.text_overlay_thread),
                    summary = stringResource(Res.string.text_overlay_thread_description),
                    checked = threadWatcherShowState,
                    onCheckedChange = {
                        if (!it) {
                            OverlayMonitor.hideThreadWatcherOverlay()
                        } else {
                            OverlayMonitor.showThreadWatcherOverlay()
                        }
                    }
                )

                SwitchPreference(
                    title = stringResource(Res.string.text_mini_load_overlay),
                    summary = stringResource(Res.string.text_mini_load_overlay_description),
                    checked = miniLoadOverlayShowState,
                    onCheckedChange = {
                        if (!it) {
                            OverlayMonitor.hideMiniLoadWatcherOverlay()
                        } else {
                            OverlayMonitor.showMiniLoadWatcherOverlay()
                        }
                    }
                )

                ArrowPreference(
                    title = stringResource(Res.string.text_fps_record_overlay),
                    summary = stringResource(Res.string.text_fps_record_overlay_description),
                    onClick = {
                        showRecordOverlay()
                    }
                )
            }
        }
    }

    // 高级重启
    Box {
        var popState by remember { mutableStateOf(false) }
        val actions = remember {
            listOf(
                RebootAction(
                    text = Res.string.text_shutdown,
                    onTap = {
                        scope.launch {
                            ReusableShells.execSync("/system/bin/svc power shutdown || /system/bin/reboot -p || /system/bin/setprop sys.powerctl shutdown")
                        }
                    }
                ),
                RebootAction(
                    text = Res.string.text_reboot,
                    onTap = {
                        scope.launch {
                            ReusableShells.execSync("/system/bin/svc power reboot || /system/bin/reboot || /system/bin/setprop sys.powerctl reboot")
                        }
                    }
                ),
                RebootAction(
                    text = Res.string.text_reboot_to_rec,
                    onTap = {
                        scope.launch {
                            ReusableShells.execSync("/system/bin/svc power reboot recovery || /system/bin/reboot recovery || /system/bin/setprop sys.powerctl reboot,recovery")
                        }
                    }
                ),
                RebootAction(
                    text = Res.string.text_reboot_to_bl,
                    onTap = {
                        scope.launch {
                            ReusableShells.execSync("/system/bin/svc power reboot bootloader || /system/bin/reboot bootloader || /system/bin/setprop sys.powerctl reboot,bootloader")
                        }
                    }
                ),
                RebootAction(
                    text = Res.string.text_reboot_to_edl,
                    onTap = {
                        scope.launch {
                            ReusableShells.execSync("/system/bin/reboot edl || /system/bin/setprop sys.powerctl reboot,edl")
                        }
                    }
                ),
            )
        }

        IconButton(
            onClick = {
                popState = true
            }
        ) {
            Icon(
                imageVector = MiuixIcons.Close2,
                contentDescription = null
            )
        }

        OverlayListPopup(
            show = popState,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { popState = false }
        ) {
            ListPopupColumn {
                actions.forEachIndexed { index, action ->
                    DropdownImpl(
                        text = stringResource(action.text),
                        optionSize = actions.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            action.onTap()
                            popState = false
                        }
                    )
                }
            }
        }
    }
}