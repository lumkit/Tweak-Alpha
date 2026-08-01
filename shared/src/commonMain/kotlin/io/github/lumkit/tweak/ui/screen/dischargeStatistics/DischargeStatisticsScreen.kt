package io.github.lumkit.tweak.ui.screen.dischargeStatistics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.AlertDialog
import io.github.lumkit.tweak.common.component.BatteryLevelAppStripChart
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.formatVoltage
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_charge
import tweak_alpha.shared.generated.resources.ic_history
import tweak_alpha.shared.generated.resources.text_charge_history_native_daemon_required
import tweak_alpha.shared.generated.resources.text_charge_history_open_native_daemon
import tweak_alpha.shared.generated.resources.text_dialog_confirm
import tweak_alpha.shared.generated.resources.text_dialog_delete_discharge_history_notes
import tweak_alpha.shared.generated.resources.text_dialog_tip
import tweak_alpha.shared.generated.resources.text_discharge_app_usage_empty
import tweak_alpha.shared.generated.resources.text_discharge_history_empty
import tweak_alpha.shared.generated.resources.text_discharge_history_title
import tweak_alpha.shared.generated.resources.text_discharge_link_charge_statistics
import tweak_alpha.shared.generated.resources.text_discharge_process
import tweak_alpha.shared.generated.resources.text_discharge_process_help
import tweak_alpha.shared.generated.resources.text_discharge_scenes
import tweak_alpha.shared.generated.resources.text_discharge_scenes_help
import tweak_alpha.shared.generated.resources.text_discharge_sort_avg_power
import tweak_alpha.shared.generated.resources.text_discharge_sort_duration
import tweak_alpha.shared.generated.resources.text_discharge_statistics
import tweak_alpha.shared.generated.resources.text_discharge_statistics_description
import tweak_alpha.shared.generated.resources.text_discharge_status_not_charging
import tweak_alpha.shared.generated.resources.text_discharge_summary
import tweak_alpha.shared.generated.resources.text_discharge_summary_avg_power
import tweak_alpha.shared.generated.resources.text_discharge_summary_duration
import tweak_alpha.shared.generated.resources.text_discharge_summary_eta
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_native_daemon_loading
import tweak_alpha.shared.generated.resources.text_battery_level

internal val DischargeStatisticsProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "DischargeStatisticsProvider",
            title = Res.string.text_discharge_statistics,
            icon = Res.drawable.ic_charge,
            description = Res.string.text_discharge_statistics_description,
            capabilities = setOf(
                Capability.SHIZUKU_OR_ROOT,
            ),
            route = Screen.DischargeStatistics,
            defaultState = FeatureState.ENABLED,
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            },
        )

    @Composable
    override fun Content() {
        DischargeStatisticsScreen()
    }
}

@Composable
fun DischargeStatisticsScreen(
    viewModel: DischargeStatisticsViewModel = viewModel { DischargeStatisticsViewModel() },
) {
    val navigator = LocalNavigator.current
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val nativeDaemonPrompt by viewModel.nativeDaemonPrompt.collectAsStateWithLifecycle()
    var nativeDaemonEnabling by remember { mutableStateOf(false) }
    var processHelpVisible by remember { mutableStateOf(false) }
    var scenesHelpVisible by remember { mutableStateOf(false) }

    viewModel.LoadStateLaunchEffect {
        Watch(DischargeStatisticsViewModel.NATIVE_DAEMON_ENABLE_LOAD_ID) {
            nativeDaemonEnabling = it is BaseViewModel.LoadState.Loading
        }
    }

    LaunchedEffect(Unit) {
        viewModel.ensureNativeDaemonOnEnter()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.ensureNativeDaemonOnEnter()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        show = nativeDaemonPrompt == true,
        summary = stringResource(Res.string.text_charge_history_native_daemon_required),
        confirmText = stringResource(Res.string.text_charge_history_open_native_daemon),
        dismissOnOutsideOrBack = false,
        onDismissRequest = {},
        onConfirm = { viewModel.enableNativeDaemon() },
        onCancel = {
            viewModel.dismissNativeDaemonPrompt()
            navigator.goBack()
        },
    )

    AlertDialog(
        show = processHelpVisible,
        summary = stringResource(Res.string.text_discharge_process_help),
        confirmText = stringResource(Res.string.text_dialog_confirm),
        onDismissRequest = { processHelpVisible = false },
        onConfirm = { processHelpVisible = false },
        onCancel = { processHelpVisible = false },
    )

    AlertDialog(
        show = scenesHelpVisible,
        summary = stringResource(Res.string.text_discharge_scenes_help),
        confirmText = stringResource(Res.string.text_dialog_confirm),
        onDismissRequest = { scenesHelpVisible = false },
        onConfirm = { scenesHelpVisible = false },
        onCancel = { scenesHelpVisible = false },
    )

    WindowDialog(
        show = nativeDaemonEnabling,
        enableWindowDim = true,
        onDismissRequest = null,
    ) {
        val blockBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = blockBackState,
            isBackEnabled = nativeDaemonEnabling,
            onBackCompleted = { },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfiniteProgressIndicator()
            Text(
                text = stringResource(Res.string.text_native_daemon_loading),
                style = MiuixTheme.textStyles.main,
            )
        }
    }

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.text_discharge_statistics),
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    navigationIcon = {
                        IconButton(onClick = navigator::goBack) {
                            Icon(
                                MiuixIcons.Back,
                                contentDescription = stringResource(Res.string.text_go_back),
                            )
                        }
                    },
                    actions = {
                        ActionHistory(viewModel)
                    },
                )
            },
            containerColor = MiuixTheme.colorScheme.surface,
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    end = padding.calculateEndPadding(direction) + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    DischargeProcessCard(
                        viewModel = viewModel,
                        onHelpClick = { processHelpVisible = true },
                    )
                }
                item {
                    DischargeSummaryCard(viewModel)
                }
                item {
                    DischargeScenesCard(
                        viewModel = viewModel,
                        onHelpClick = { scenesHelpVisible = true },
                    )
                }
                item {
                    DischargeBottomLinks(
                        onChargeStatistics = {
                            navigator.navigate(Screen.ChargeStatistics)
                        },
                    )
                }
            }
        }
    }
}

private const val PLACEHOLDER = "—"

@Composable
private fun DischargeProcessCard(
    viewModel: DischargeStatisticsViewModel,
    onHelpClick: () -> Unit,
) {
    val hasSamples by viewModel.hasChartSamples.collectAsStateWithLifecycle()
    val levelSeries by viewModel.levelSeries.collectAsStateWithLifecycle()
    val levelXAxis by viewModel.levelXAxis.collectAsStateWithLifecycle()
    val appSegments by viewModel.appSegments.collectAsStateWithLifecycle()
    val batteryInfo by viewModel.batteryInfoRow.collectAsStateWithLifecycle()
    val currentLevel by viewModel.currentLevel.collectAsStateWithLifecycle()
    val levelLabel = stringResource(Res.string.text_battery_level)
    val notCharging = stringResource(Res.string.text_discharge_status_not_charging)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(
                text = stringResource(Res.string.text_discharge_process),
                insideMargin = PaddingValues(0.dp),
            )
            IconButton(
                onClick = onHelpClick,
                modifier = Modifier.size(28.dp),
            ) {
                Text(
                    text = "?",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = currentLevel?.let { "$it%" } ?: PLACEHOLDER,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        if (!hasSamples || levelSeries == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.text_discharge_history_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        } else {
            val series = levelSeries!!
            BatteryLevelAppStripChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                levelSeries = series.copy(name = levelLabel),
                xAxis = levelXAxis,
                appSegments = appSegments,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val energyText = batteryInfo?.let { formatDischargeEnergyWh(it.energyUw) } ?: PLACEHOLDER
            val tempText = batteryInfo?.temperatureC?.let { "%.1f℃".format(it) } ?: PLACEHOLDER
            val voltageText = batteryInfo?.voltageMv?.formatVoltage() ?: PLACEHOLDER
            InfoChip(text = energyText)
            InfoChip(text = tempText)
            InfoChip(text = voltageText)
            InfoChip(text = notCharging)
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
    )
}

@Composable
private fun DischargeSummaryCard(viewModel: DischargeStatisticsViewModel) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val etaText by viewModel.etaText.collectAsStateWithLifecycle()

    val avgPowerText = summary?.averagePowerUw?.takeIf { it > 0L }?.let {
        (it / 1000L).toInt().formatPower()
    } ?: PLACEHOLDER
    val durationText = summary?.let {
        val endedAt = if (it.isCurrentDischargingSession) currentTime else it.endedAt
        formatDischargeSessionDuration(it.startedAt, endedAt)
    } ?: PLACEHOLDER

    Card(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = stringResource(Res.string.text_discharge_summary))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryMetric(
                title = stringResource(Res.string.text_discharge_summary_avg_power),
                value = avgPowerText,
            )
            SummaryMetric(
                title = stringResource(Res.string.text_discharge_summary_duration),
                value = durationText,
            )
            SummaryMetric(
                title = stringResource(Res.string.text_discharge_summary_eta),
                value = etaText,
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    title: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun DischargeScenesCard(
    viewModel: DischargeStatisticsViewModel,
    onHelpClick: () -> Unit,
) {
    val appRows by viewModel.appRows.collectAsStateWithLifecycle()
    val sortMode by viewModel.appSortMode.collectAsStateWithLifecycle()
    var sortPopupVisible by remember { mutableStateOf(false) }
    val sortDuration = stringResource(Res.string.text_discharge_sort_duration)
    val sortAvgPower = stringResource(Res.string.text_discharge_sort_avg_power)
    val sortLabel = when (sortMode) {
        DischargeStatisticsViewModel.AppSortMode.Duration -> sortDuration
        DischargeStatisticsViewModel.AppSortMode.AvgPower -> sortAvgPower
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(text = stringResource(Res.string.text_discharge_scenes))
            IconButton(
                onClick = onHelpClick,
                modifier = Modifier.size(28.dp),
            ) {
                Text(
                    text = "?",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(Rectangle.copy(cornerRadius = 4.dp))
                    .clickable { sortPopupVisible = true }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = sortLabel,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                    ),
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.31f),
                )
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.75f),
                )
            }
            OverlayListPopup(
                show = sortPopupVisible,
                alignment = PopupPositionProvider.Align.End,
                onDismissRequest = { sortPopupVisible = false },
            ) {
                ListPopupColumn {
                    DischargeStatisticsViewModel.AppSortMode.entries.forEachIndexed { index, entry ->
                        DropdownImpl(
                            text = when (entry) {
                                DischargeStatisticsViewModel.AppSortMode.Duration -> sortDuration
                                DischargeStatisticsViewModel.AppSortMode.AvgPower -> sortAvgPower
                            },
                            optionSize = DischargeStatisticsViewModel.AppSortMode.entries.size,
                            isSelected = sortMode == entry,
                            index = index,
                            onSelectedIndexChange = {
                                viewModel.setAppSortMode(entry)
                                sortPopupVisible = false
                            },
                        )
                    }
                }
            }
        }

        if (appRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.text_discharge_app_usage_empty),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.31f),
                )
            }
        } else {
            Column {
                appRows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    DischargeAppUsageItem(row)
                }
            }
        }
    }
}

@Composable
private fun DischargeAppUsageItem(row: DischargeStatisticsViewModel.AppUsageRow) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        startAction = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                AsyncImage(
                    model = row.iconPath,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                )
            }
        },
        endActions = {
            Text(
                text = formatDischargeSessionDuration(0L, row.durationMs),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        },
    ) {
        Text(
            text = row.appName,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.footnote1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDischargeAppAvgLine(row.avgW, row.avgTemp),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
            ),
        )
        Text(
            text = formatDischargeAppMaxTemp(row.maxTemp),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.31f),
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
            ),
        )
    }
}

@Composable
private fun DischargeBottomLinks(
    onChargeStatistics: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(Res.string.text_discharge_link_charge_statistics),
            onClick = onChargeStatistics,
        )
    }
}

@Composable
private fun ActionHistory(
    viewModel: DischargeStatisticsViewModel,
) {
    var showSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val historyState by viewModel.dischargeHistoryUiState.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val displayedChartsSessionId by viewModel.displayedChartsSessionId.collectAsStateWithLifecycle()

    val selectAllState = when {
        historyState.items.isEmpty() -> ToggleableState.Off
        historyState.selectedSessionIds.size == historyState.items.size -> ToggleableState.On
        historyState.selectedSessionIds.isEmpty() -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
    val deleteEnabled = historyState.selectedSessionIds.isNotEmpty() && !historyState.isDeleting
    val deleteAlpha by animateFloatAsState(
        targetValue = if (deleteEnabled) 1f else 0.31f,
    )

    BackHandler(enabled = showSheet && historyState.isSelectionMode) {
        viewModel.exitHistorySelectionMode()
    }

    IconButton(onClick = { showSheet = true }) {
        Icon(
            painter = painterResource(Res.drawable.ic_history),
            contentDescription = stringResource(Res.string.text_discharge_history_title),
            modifier = Modifier.size(24.dp),
        )
    }

    OverlayBottomSheet(
        show = showSheet,
        title = stringResource(Res.string.text_discharge_history_title),
        startAction = {
            IconButton(
                onClick = {
                    if (historyState.isSelectionMode) {
                        viewModel.exitHistorySelectionMode()
                    } else {
                        showSheet = false
                    }
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(Res.string.text_go_back),
                )
            }
        },
        endAction = if (historyState.isSelectionMode) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = deleteEnabled,
                        modifier = Modifier.alpha(deleteAlpha),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = viewModel::toggleHistorySelectAll) {
                        Checkbox(
                            state = selectAllState,
                            onClick = null,
                        )
                    }
                }
            }
        } else {
            null
        },
        onDismissRequest = {
            showSheet = false
            showDeleteDialog = false
            viewModel.onHistorySheetClosed()
        },
    ) {
        LaunchedEffect(Unit) {
            viewModel.onHistorySheetOpened()
        }

        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .heightIn(max = 560.dp),
        ) {
            when {
                historyState.isLoading && historyState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                historyState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.text_discharge_history_empty),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.31f),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = historyState.items,
                            key = { it.sessionId },
                        ) { item ->
                            val selected = item.sessionId in historyState.selectedSessionIds
                            val highlighted = !historyState.isSelectionMode &&
                                item.sessionId == displayedChartsSessionId
                            DischargeHistoryListItem(
                                title = formatDischargeSessionStartTitle(item.startedAt),
                                subtitle = formatDischargeSessionSummaryLine(item.summary, currentTime),
                                selectionMode = historyState.isSelectionMode,
                                selected = selected,
                                highlighted = highlighted,
                                onClick = {
                                    if (historyState.isSelectionMode) {
                                        viewModel.toggleHistorySelection(item.sessionId)
                                    } else {
                                        viewModel.selectHistorySessionForCharts(item.sessionId)
                                    }
                                },
                                onLongClick = {
                                    viewModel.onHistoryLongPress(item.sessionId)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    OverlayDialog(
        title = stringResource(Res.string.text_dialog_tip),
        summary = stringResource(Res.string.text_dialog_delete_discharge_history_notes),
        show = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false },
    ) {
        TextButton(
            text = stringResource(Res.string.text_dialog_confirm),
            onClick = {
                showDeleteDialog = false
                viewModel.deleteSelectedDischargeHistory()
            },
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DischargeHistoryListItem(
    title: String,
    subtitle: String,
    selectionMode: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val containerColor = if (highlighted) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    BasicComponent(
        modifier = Modifier
            .clip(Rectangle.copy(12.dp))
            .fillMaxWidth()
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        startAction = {
            Icon(
                painter = painterResource(Res.drawable.ic_charge),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        endActions = {
            if (selectionMode) {
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = null,
                )
            }
        },
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.footnote1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.31f),
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 9.sp,
                lineHeight = 9.sp,
            ),
        )
    }
}
