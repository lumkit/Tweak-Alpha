package io.github.lumkit.tweak.ui.screen.chargeStatistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_charge
import tweak_alpha.shared.generated.resources.text_battery_level
import tweak_alpha.shared.generated.resources.text_charge_chart_current_time
import tweak_alpha.shared.generated.resources.text_charge_chart_level_time
import tweak_alpha.shared.generated.resources.text_charge_chart_mode_current
import tweak_alpha.shared.generated.resources.text_charge_chart_mode_power
import tweak_alpha.shared.generated.resources.text_charge_chart_power_time
import tweak_alpha.shared.generated.resources.text_charge_chart_temperature_time
import tweak_alpha.shared.generated.resources.text_charge_label_average_power
import tweak_alpha.shared.generated.resources.text_charge_label_battery_capacity
import tweak_alpha.shared.generated.resources.text_charge_label_battery_power
import tweak_alpha.shared.generated.resources.text_charge_label_battery_status
import tweak_alpha.shared.generated.resources.text_charge_label_battery_status_charging
import tweak_alpha.shared.generated.resources.text_charge_label_battery_status_discharging
import tweak_alpha.shared.generated.resources.text_charge_label_battery_temperature
import tweak_alpha.shared.generated.resources.text_charge_label_battery_voltage
import tweak_alpha.shared.generated.resources.text_charge_label_current
import tweak_alpha.shared.generated.resources.text_charge_statistics
import tweak_alpha.shared.generated.resources.text_charge_statistics_description
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_go_back
import kotlin.time.Instant

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
                item {
                    ChargePowerTimeChart(viewModel)
                }
                item {
                    ChargeLevelTimeChart(viewModel)
                }
                item {
                    ChargeTemperatureTimeChart(viewModel)
                }
            }
        }
    }
}

@Composable
private fun ChargeStateContent(viewModel: ChargeStatisticsViewModel) {
    val state = viewModel.chartState
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
                    text = buildSessionTimeText(
                        summary.startedAt,
                        endedAt,
                        summary.isCurrentChargingSession,
                    ),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSessionDuration(summary.startedAt, endedAt),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatLevelGain(summary.startLevel, summary.endLevel ?: summary.startLevel),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatEnergyGainWh(summary.energyGainUw),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                )
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

private fun buildSessionTimeText(startedAt: Long, endedAt: Long, showNow: Boolean): String {
    val zone = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(startedAt).toLocalDateTime(zone)
    val end = Instant.fromEpochMilliseconds(endedAt).toLocalDateTime(zone)
    val startText = "%04d-%02d-%02d %02d:%02d:%02d".format(
        start.year,
        start.month.number,
        start.day,
        start.hour,
        start.minute,
        start.second,
    )
    val endText = if (showNow) {
        "现在"
    } else if (
        start.year == end.year &&
        start.month.number == end.month.number &&
        start.day == end.day
    ) {
        "%02d:%02d:%02d".format(end.hour, end.minute, end.second)
    } else {
        "%04d-%02d-%02d %02d:%02d:%02d".format(
            end.year,
            end.month.number,
            end.day,
            end.hour,
            end.minute,
            end.second,
        )
    }
    return "$startText ~ $endText"
}

private fun formatSessionDuration(startedAt: Long, endedAt: Long): String {
    val elapsed = (endedAt - startedAt).coerceAtLeast(0L)
    val second = 1_000L
    val minute = 60 * second
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        elapsed >= day -> buildString {
            val days = elapsed / day
            val hours = (elapsed % day) / hour
            append("${days}天")
            if (hours > 0) append("${hours}小时")
        }
        elapsed >= hour -> buildString {
            val hours = elapsed / hour
            val minutes = (elapsed % hour) / minute
            append("${hours}小时")
            if (minutes > 0) append("${minutes}分钟")
        }
        elapsed >= minute -> buildString {
            val minutes = elapsed / minute
            val seconds = (elapsed % minute) / second
            append("${minutes}分钟")
            if (seconds > 0) append("${seconds}秒")
        }
        else -> "${elapsed / second}秒"
    }
}

private fun formatLevelGain(startLevel: Int, endLevel: Int): String {
    val delta = endLevel - startLevel
    return if (delta >= 0) "+${delta}%" else "${delta}%"
}

private fun formatEnergyGainWh(energyGainUw: Long): String {
    val wh = energyGainUw / 1_000_000L
    val fraction = kotlin.math.abs((energyGainUw % 1_000_000L) / 10_000L)
    return if (fraction == 0L) {
        "${wh}Wh"
    } else {
        "${wh}.${fraction.toString().padStart(2, '0')}Wh"
    }
}

private enum class ChargePowerChartMode {
    Power,
    Current,
}

@Composable
private fun ChargePowerTimeChart(viewModel: ChargeStatisticsViewModel) {
    val samples by viewModel.chartSamples.collectAsStateWithLifecycle()
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
private fun ChargeLevelTimeChart(viewModel: ChargeStatisticsViewModel) {
    val samples by viewModel.chartSamples.collectAsStateWithLifecycle()
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
private fun ChargeTemperatureTimeChart(viewModel: ChargeStatisticsViewModel) {
    val samples by viewModel.chartSamples.collectAsStateWithLifecycle()
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
