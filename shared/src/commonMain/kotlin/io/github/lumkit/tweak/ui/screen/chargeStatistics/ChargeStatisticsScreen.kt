package io.github.lumkit.tweak.ui.screen.chargeStatistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.LintCurveChart
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.utils.formatCurrent
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.formatVoltage
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_charge
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
                item("ChargeStateContent") {
                    ChargeStateContent(viewModel)
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

        // 当前充电session状态
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
