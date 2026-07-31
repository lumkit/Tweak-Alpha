package io.github.lumkit.tweak.ui.screen.chargeStatistics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.Dp
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.AlertDialog
import io.github.lumkit.tweak.common.component.LineChartAxisType
import io.github.lumkit.tweak.common.component.LineChartData
import io.github.lumkit.tweak.common.component.LineChartXAxisData
import io.github.lumkit.tweak.common.component.LintCurveChart
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.SmoothLineChart
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.utils.formatCurrent
import io.github.lumkit.tweak.common.utils.formatElapsedTime
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.formatVoltage
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import tweak_alpha.shared.generated.resources.ic_charge_fill
import tweak_alpha.shared.generated.resources.ic_history
import tweak_alpha.shared.generated.resources.text_battery_level
import tweak_alpha.shared.generated.resources.text_charge_chart_current_time
import tweak_alpha.shared.generated.resources.text_charge_chart_level_time
import tweak_alpha.shared.generated.resources.text_charge_chart_mode_current
import tweak_alpha.shared.generated.resources.text_charge_chart_mode_power
import tweak_alpha.shared.generated.resources.text_charge_chart_power_time
import tweak_alpha.shared.generated.resources.text_charge_chart_temperature_time
import tweak_alpha.shared.generated.resources.text_charge_history_empty
import tweak_alpha.shared.generated.resources.text_charge_history_native_daemon_required
import tweak_alpha.shared.generated.resources.text_charge_history_open_native_daemon
import tweak_alpha.shared.generated.resources.text_charge_history_title
import tweak_alpha.shared.generated.resources.text_charge_label_average_power
import tweak_alpha.shared.generated.resources.text_charge_label_battery_capacity
import tweak_alpha.shared.generated.resources.text_charge_label_battery_power
import tweak_alpha.shared.generated.resources.text_charge_label_battery_status
import tweak_alpha.shared.generated.resources.text_charge_label_battery_status_charging
import tweak_alpha.shared.generated.resources.text_charge_label_battery_status_discharging
import tweak_alpha.shared.generated.resources.text_charge_label_battery_status_full
import tweak_alpha.shared.generated.resources.text_charge_label_battery_temperature
import tweak_alpha.shared.generated.resources.text_charge_label_battery_voltage
import tweak_alpha.shared.generated.resources.text_charge_label_current
import tweak_alpha.shared.generated.resources.text_charge_statistics
import tweak_alpha.shared.generated.resources.text_charge_statistics_description
import tweak_alpha.shared.generated.resources.text_dialog_confirm
import tweak_alpha.shared.generated.resources.text_dialog_delete_charge_history_notes
import tweak_alpha.shared.generated.resources.text_dialog_tip
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_native_daemon_loading

internal val ChargeStatisticsProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "ChargeStatisticsProvider",
            title = Res.string.text_charge_statistics,
            icon = Res.drawable.ic_charge,
            description = Res.string.text_charge_statistics_description,
            capabilities = setOf(
                Capability.SHIZUKU_OR_ROOT,
            ),
            route = Screen.ChargeStatistics,
            defaultState = FeatureState.ENABLED,
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            },
        )

    @Composable
    override fun Content() {
        ChargeStatisticsScreen()
    }
}

@Composable
fun ChargeStatisticsScreen(
    viewModel: ChargeStatisticsViewModel = viewModel { ChargeStatisticsViewModel() },
) {
    val navigator = LocalNavigator.current
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val nativeDaemonPrompt by viewModel.chargeHistoryNativeDaemonPrompt.collectAsStateWithLifecycle()
    var nativeDaemonEnabling by remember { mutableStateOf(false) }
    val chargeSample by viewModel.chartSamples.collectAsStateWithLifecycle()

    viewModel.LoadStateLaunchEffect {
        Watch(ChargeStatisticsViewModel.NATIVE_DAEMON_ENABLE_LOAD_ID) {
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
        onConfirm = { viewModel.enableNativeDaemonForChargeHistory() },
        onCancel = {
            viewModel.dismissChargeHistoryNativeDaemonPrompt()
            navigator.goBack()
        },
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
                    title = stringResource(Res.string.text_charge_statistics),
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
                    }
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
                    ChargeStateContent(viewModel)
                }
                if (chargeSample.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.text_charge_history_empty),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(.5f)
                            )
                        }
                    }
                } else {
                    item {
                        ChargePowerTimeChart( chargeSample)
                    }
                    item {
                        ChargeLevelTimeChart(chargeSample)
                    }
                    item {
                        ChargeTemperatureTimeChart(chargeSample)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeStateContent(viewModel: ChargeStatisticsViewModel) {
    val state = viewModel.chartState
    val chargeHistory by viewModel.chartSamples.collectAsStateWithLifecycle()
    val chargeState by viewModel.chargeState.collectAsStateWithLifecycle()
    val currentPowerMw by viewModel.currentPowerMw.collectAsStateWithLifecycle()
    val currentMa by viewModel.currentMa.collectAsStateWithLifecycle()
    val averagePowerUw by viewModel.averagePower.collectAsStateWithLifecycle()
    val batteryTemperatureC by viewModel.batteryTemperatureC.collectAsStateWithLifecycle()
    val batteryVoltageMv by viewModel.batteryVoltageMv.collectAsStateWithLifecycle()
    val batterySnapshot by viewModel.batterySnapshot.collectAsStateWithLifecycle()
    val currentSessionSummary by viewModel.currentSessionSummary.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()

    val batteryStatusText = when (chargeState) {
        BatteryChargeState.CHARGING -> stringResource(Res.string.text_charge_label_battery_status_charging)
        BatteryChargeState.FULL -> stringResource(Res.string.text_charge_label_battery_status_full)
        BatteryChargeState.DISCHARGING,
        null,
        -> stringResource(Res.string.text_charge_label_battery_status_discharging)
    }
    val powerText = currentPowerMw?.formatPower() ?: PLACEHOLDER
    val currentText = currentMa?.formatCurrent() ?: PLACEHOLDER
    val averagePowerText = if (averagePowerUw > 0L) {
        (averagePowerUw / 1000L).toInt().formatPower()
    } else {
        PLACEHOLDER
    }
    val temperatureText = batteryTemperatureC?.let { "%.1f℃".format(it) } ?: PLACEHOLDER
    val voltageText = batteryVoltageMv?.formatVoltage() ?: PLACEHOLDER
    val batteryCapacityText = remember(batterySnapshot) {
        val snapshot = batterySnapshot
        snapshot?.designCapacityMah?.let { designCapacity ->
            val cycleCount = snapshot.cycleCount
            if (cycleCount != null) {
                "$designCapacity mAh（$cycleCount）"
            } else {
                "$designCapacity mAh"
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 电池状态Labels
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SmallTitle(
                    text = buildString {
                        append(stringResource(Res.string.text_charge_label_battery_status))
                        append(" - ")
                        append(batteryStatusText)
                    },
                    insideMargin = PaddingValues(bottom = 12.dp)
                )
                ChargeStateLabel(
                    title = stringResource(Res.string.text_charge_label_battery_temperature),
                    value = temperatureText,
                )
                ChargeStateLabel(
                    title = stringResource(Res.string.text_charge_label_battery_voltage),
                    value = voltageText,
                )
                ChargeStateLabel(
                    title = stringResource(Res.string.text_charge_label_current),
                    value = currentText,
                )
                if (batteryCapacityText != null) {
                    ChargeStateLabel(
                        title = stringResource(Res.string.text_charge_label_battery_capacity),
                        value = batteryCapacityText,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LintCurveChart(
                    modifier = Modifier.fillMaxWidth()
                        .height(72.dp),
                    state = state,
                )
                ChargeStateLabel(
                    title = stringResource(Res.string.text_charge_label_average_power),
                    value = averagePowerText,
                )
                ChargeStateLabel(
                    title = stringResource(Res.string.text_charge_label_battery_power),
                    value = powerText,
                )
            }
        }

        AnimatedVisibility(
            visible = chargeHistory.isNotEmpty()
        ) {
            Column {
                Spacer(modifier = Modifier.width(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.width(12.dp))

                // session摘要
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    currentSessionSummary?.let { summary ->
                        val endedAt = if (summary.isCurrentChargingSession) currentTime else summary.endedAt
                        Text(
                            text = buildChargeSessionTimeText(
                                summary.startedAt,
                                endedAt,
                                summary.isCurrentChargingSession,
                            ),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatChargeSessionDuration(summary.startedAt, endedAt),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatChargeLevelGain(summary.startLevel, summary.endLevel ?: summary.startLevel),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatChargeEnergyGainWh(summary.energyGainUw),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                        )
                    }
                }
            }
        }
    }
}

private const val PLACEHOLDER = "—"

@Composable
private fun ChargeStateLabel(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
        )

        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface.copy(.75f),
        )
    }
}

private enum class ChargePowerChartMode {
    Power,
    Current,
}

@Composable
private fun ChargePowerTimeChart(
    samples: List<ChargeStatisticsViewModel.ChargeChartSample>
) {
    var mode by rememberSaveable { mutableStateOf(ChargePowerChartMode.Power) }
    var modePopupVisible by remember { mutableStateOf(false) }
    val primaryColor = MiuixTheme.colorScheme.primary.copy(alpha = .5f)
    val batteryLevelColor = Color(0xFFFF8A65).copy(alpha = .31f)
    val powerTitle = stringResource(Res.string.text_charge_chart_power_time)
    val currentTitle = stringResource(Res.string.text_charge_chart_current_time)
    val modePowerLabel = stringResource(Res.string.text_charge_chart_mode_power)
    val modeCurrentLabel = stringResource(Res.string.text_charge_chart_mode_current)
    val batteryLevelLabel = stringResource(Res.string.text_battery_level)
    val title = when (mode) {
        ChargePowerChartMode.Power -> powerTitle
        ChargePowerChartMode.Current -> currentTitle
    }
    val modeLabel = when (mode) {
        ChargePowerChartMode.Power -> modePowerLabel
        ChargePowerChartMode.Current -> modeCurrentLabel
    }

    val snapshot by produceState(initialValue = ChargeLineChartSnapshot.Empty, samples, mode) {
        value = withContext(Dispatchers.Default) {
            buildChargePowerChartSnapshot(
                samples = samples,
                mode = mode,
                powerTitle = powerTitle,
                currentTitle = currentTitle,
                batteryLevelLabel = batteryLevelLabel,
                primaryColor = primaryColor,
                batteryLevelColor = batteryLevelColor,
            )
        }
    }
    val chartSnapshot = rememberStableChargeChartSnapshot(samples, snapshot)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallTitle(text = title)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(Rectangle.copy(cornerRadius = 4.dp))
                    .clickable { modePopupVisible = true }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = modeLabel,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                    ),
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                )
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary.copy(.75f),
                )
            }
            OverlayListPopup(
                show = modePopupVisible,
                alignment = PopupPositionProvider.Align.End,
                onDismissRequest = { modePopupVisible = false },
            ) {
                ListPopupColumn {
                    ChargePowerChartMode.entries.forEachIndexed { index, entry ->
                        DropdownImpl(
                            text = when (entry) {
                                ChargePowerChartMode.Power -> modePowerLabel
                                ChargePowerChartMode.Current -> modeCurrentLabel
                            },
                            optionSize = ChargePowerChartMode.entries.size,
                            isSelected = mode == entry,
                            index = index,
                            onSelectedIndexChange = {
                                mode = entry
                                modePopupVisible = false
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        ChargeSmoothLineChartBody(
            endPadding = 0.dp,
            snapshot = chartSnapshot,
            showAffix = true,
        )
        ChargeChartColorIndicator(chartSnapshot.chartData, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun ChargeLevelTimeChart(
    samples: List<ChargeStatisticsViewModel.ChargeChartSample>
) {
    val primaryColor = MiuixTheme.colorScheme.primary.copy(alpha = .5f)
    val title = stringResource(Res.string.text_charge_chart_level_time)

    val snapshot by produceState(initialValue = ChargeLineChartSnapshot.Empty, samples) {
        value = withContext(Dispatchers.Default) {
            buildChargeSingleSeriesSnapshot(
                samples = samples,
                title = title,
                suffix = "%",
                primaryColor = primaryColor,
                valueSelector = { it.level },
            )
        }
    }
    val chartSnapshot = rememberStableChargeChartSnapshot(samples, snapshot)

    Card(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        Spacer(modifier = Modifier.height(4.dp))
        ChargeSmoothLineChartBody(endPadding = 16.dp, snapshot = chartSnapshot)
        ChargeChartColorIndicator(chartSnapshot.chartData, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun ChargeTemperatureTimeChart(
    samples: List<ChargeStatisticsViewModel.ChargeChartSample>
) {
    val primaryColor = MiuixTheme.colorScheme.primary.copy(alpha = .5f)
    val title = stringResource(Res.string.text_charge_chart_temperature_time)

    val snapshot by produceState(initialValue = ChargeLineChartSnapshot.Empty, samples) {
        value = withContext(Dispatchers.Default) {
            buildChargeSingleSeriesSnapshot(
                samples = samples,
                title = title,
                suffix = "℃",
                primaryColor = primaryColor,
                valueSelector = { it.temperatureC },
            )
        }
    }
    val chartSnapshot = rememberStableChargeChartSnapshot(samples, snapshot)

    Card(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        Spacer(modifier = Modifier.height(4.dp))
        ChargeSmoothLineChartBody(endPadding = 16.dp, snapshot = chartSnapshot)
        ChargeChartColorIndicator(chartSnapshot.chartData, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Immutable
private data class ChargeLineChartSnapshot(
    val xAxis: LineChartXAxisData,
    val chartData: List<LineChartData>,
) {
    val isDrawable: Boolean
        get() = chartData.isNotEmpty() &&
            xAxis.dataSet.isNotEmpty() &&
            chartData.all { it.dataSet.size == xAxis.dataSet.size }

    companion object {
        val Empty = ChargeLineChartSnapshot(LineChartXAxisData(emptyList()), emptyList())
    }
}

/**
 * x 轴与序列在同一帧内构建，避免两个 [produceState] 先后完成导致尺寸不一致而闪空白。
 * 无采样时不用上一帧缓存，避免会话清空后仍显示旧曲线。
 */
@Composable
private fun rememberStableChargeChartSnapshot(
    samples: List<ChargeStatisticsViewModel.ChargeChartSample>,
    snapshot: ChargeLineChartSnapshot,
): ChargeLineChartSnapshot {
    var lastDrawable by remember { mutableStateOf(ChargeLineChartSnapshot.Empty) }
    SideEffect {
        if (snapshot.isDrawable) {
            lastDrawable = snapshot
        }
    }
    return when {
        snapshot.isDrawable -> snapshot
        samples.isEmpty() -> ChargeLineChartSnapshot.Empty
        lastDrawable.isDrawable -> lastDrawable
        else -> snapshot
    }
}

private fun buildChargeXAxis(samples: List<ChargeStatisticsViewModel.ChargeChartSample>): LineChartXAxisData {
    return LineChartXAxisData(
        dataSet = samples.map { it.elapsedMs.formatElapsedTime() },
    )
}

private fun buildChargePowerChartSnapshot(
    samples: List<ChargeStatisticsViewModel.ChargeChartSample>,
    mode: ChargePowerChartMode,
    powerTitle: String,
    currentTitle: String,
    batteryLevelLabel: String,
    primaryColor: Color,
    batteryLevelColor: Color,
): ChargeLineChartSnapshot {
    if (samples.isEmpty()) return ChargeLineChartSnapshot.Empty
    val primary = when (mode) {
        ChargePowerChartMode.Power -> LineChartData(
            name = powerTitle,
            suffix = "W",
            dataSet = samples.map { it.powerW },
            color = primaryColor,
            axisType = LineChartAxisType.Primary,
        )
        ChargePowerChartMode.Current -> LineChartData(
            name = currentTitle,
            suffix = "mA",
            dataSet = samples.map { it.currentMa },
            color = primaryColor,
            axisType = LineChartAxisType.Primary,
        )
    }
    return ChargeLineChartSnapshot(
        xAxis = buildChargeXAxis(samples),
        chartData = listOf(
            primary,
            LineChartData(
                name = batteryLevelLabel,
                suffix = "",
                dataSet = samples.map { it.level },
                color = batteryLevelColor,
                axisType = LineChartAxisType.Secondary,
            ),
        ),
    )
}

private fun buildChargeSingleSeriesSnapshot(
    samples: List<ChargeStatisticsViewModel.ChargeChartSample>,
    title: String,
    suffix: String,
    primaryColor: Color,
    valueSelector: (ChargeStatisticsViewModel.ChargeChartSample) -> Float,
): ChargeLineChartSnapshot {
    if (samples.isEmpty()) return ChargeLineChartSnapshot.Empty
    return ChargeLineChartSnapshot(
        xAxis = buildChargeXAxis(samples),
        chartData = listOf(
            LineChartData(
                name = title,
                suffix = suffix,
                dataSet = samples.map(valueSelector),
                color = primaryColor,
                axisType = LineChartAxisType.Primary,
            ),
        ),
    )
}

@Composable
private fun ChargeSmoothLineChartBody(
    endPadding: Dp,
    snapshot: ChargeLineChartSnapshot,
    showAffix: Boolean = false,
) {
    if (snapshot.isDrawable) {
        SmoothLineChart(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = endPadding)
                .height(250.dp),
            xAxis = snapshot.xAxis,
            data = snapshot.chartData,
            axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f),
            tickTextColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
            lineWidth = 1.dp,
            showGrid = true,
            gridColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .08f),
            textStyle = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
            ),
            showAffix = showAffix,
        )
    } else {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
        )
    }
}

@Composable
private fun ChargeChartColorIndicator(
    chartData: List<LineChartData>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chartData.forEach { series ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(Rectangle.copy(4.dp))
                        .background(series.color),
                )
                Text(
                    text = series.name,
                    style = MiuixTheme.textStyles.footnote2,
                    color = series.color,
                )
            }
        }
    }
}

@Composable
private fun ActionHistory(
    viewModel: ChargeStatisticsViewModel,
) {
    var showSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val historyState by viewModel.chargeHistoryUiState.collectAsStateWithLifecycle()
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
        viewModel.exitChargeHistorySelectionMode()
    }

    IconButton(
        onClick = { showSheet = true },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_history),
            contentDescription = stringResource(Res.string.text_charge_history_title),
            modifier = Modifier.size(24.dp),
        )
    }

    OverlayBottomSheet(
        show = showSheet,
        title = stringResource(Res.string.text_charge_history_title),
        startAction = {
            IconButton(
                onClick = {
                    if (historyState.isSelectionMode) {
                        viewModel.exitChargeHistorySelectionMode()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    IconButton(onClick = viewModel::toggleChargeHistorySelectAll) {
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
            viewModel.onChargeHistorySheetClosed()
        },
    ) {
        LaunchedEffect(Unit) {
            viewModel.onChargeHistorySheetOpened()
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
                            text = stringResource(Res.string.text_charge_history_empty),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
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
                            ChargeHistoryListItem(
                                title = formatChargeSessionStartTitle(item.startedAt),
                                subtitle = formatChargeSessionSummaryLine(item.summary, currentTime),
                                selectionMode = historyState.isSelectionMode,
                                selected = selected,
                                highlighted = highlighted,
                                onClick = {
                                    if (historyState.isSelectionMode) {
                                        viewModel.toggleChargeHistorySelection(item.sessionId)
                                    } else {
                                        viewModel.selectChargeHistorySessionForCharts(item.sessionId)
                                    }
                                },
                                onLongClick = {
                                    viewModel.onChargeHistoryLongPress(item.sessionId)
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
        summary = stringResource(Res.string.text_dialog_delete_charge_history_notes),
        show = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false },
    ) {
        TextButton(
            text = stringResource(Res.string.text_dialog_confirm),
            onClick = {
                showDeleteDialog = false
                viewModel.deleteSelectedChargeHistory()
            },
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChargeHistoryListItem(
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
                painter = painterResource(Res.drawable.ic_charge_fill),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
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
