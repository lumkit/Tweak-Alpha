package io.github.lumkit.tweak.ui.screen.fpsRecord

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.component.LineChartAxisType
import io.github.lumkit.tweak.common.component.LineChartData
import io.github.lumkit.tweak.common.component.LineChartXAxisData
import io.github.lumkit.tweak.common.component.SmoothLineChart
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.component.VerticalBarChart
import io.github.lumkit.tweak.common.component.VerticalBarChartStyleType
import io.github.lumkit.tweak.common.database.fps.table.FpsRecordDetailAggregate
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.formatElapsedTime
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_platform_device_style
import tweak_alpha.shared.generated.resources.ic_soc
import tweak_alpha.shared.generated.resources.ic_system_version
import tweak_alpha.shared.generated.resources.text_avg
import tweak_alpha.shared.generated.resources.text_battery_level
import tweak_alpha.shared.generated.resources.text_cpu_load_total
import tweak_alpha.shared.generated.resources.text_cpu_loads
import tweak_alpha.shared.generated.resources.text_cpu_loads_source
import tweak_alpha.shared.generated.resources.text_diff
import tweak_alpha.shared.generated.resources.text_fps_recording
import tweak_alpha.shared.generated.resources.text_frame_time
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_max_high
import tweak_alpha.shared.generated.resources.text_min_low
import tweak_alpha.shared.generated.resources.text_platform_model
import tweak_alpha.shared.generated.resources.text_platform_type
import tweak_alpha.shared.generated.resources.text_platform_version
import tweak_alpha.shared.generated.resources.text_power
import tweak_alpha.shared.generated.resources.text_soc_load
import tweak_alpha.shared.generated.resources.text_temperature
import kotlin.math.roundToInt

private const val TAG = "FpsRecordDetailScreen"

@Composable
fun FpsRecordDetailScreen(
    sessionId: Long,
    viewModel: FpsRecordDetailViewModel = viewModel(key = "sessionId-$sessionId") {
        FpsRecordDetailViewModel(
            sessionId
        )
    }
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backdrop = rememberLayerBackdropColor()
    val direction = LocalLayoutDirection.current

    val detail by viewModel.detail.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.text_fps_recording),
                subTitle = "${detail?.appName ?: ""} - ${detail?.appPackageName ?: ""}",
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                navigationIcon = {
                    IconButton(
                        onClick = navigator::goBack
                    ) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(Res.string.text_go_back),
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
        floatingToolbarPosition = ToolbarPosition.BottomEnd
    ) {
        LazyColumn(
            modifier = Modifier.layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = it.calculateStartPadding(direction) + 16.dp,
                top = it.calculateTopPadding() + 16.dp,
                end = it.calculateEndPadding(direction) + 16.dp,
                bottom = it.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DeviceInfoContent(
                    platformName = detail?.platformName ?: "",
                    deviceName = detail?.deviceModel ?: "",
                    platformVersionName = detail?.platformVersionName ?: "",
                )
            }

            item {
                RecordAppInfo(
                    iconPath = detail?.appIconPath,
                    recordDate = detail?.recordDate ?: "--",
                    appName = detail?.appName ?: "--",
                    appVersionInfo = detail?.appVersion ?: "--",
                    fpsMax = detail?.maxFps ?: -1.0,
                    fpsMin = detail?.minFps ?: -1.0,
                    fpsAvg = detail?.avgFps ?: -1.0,
                    fpsVariance = detail?.fpsDiff ?: -1.0,
                    fpsLow45 = (detail?.above45FpsRatio ?: 0.0).toFloat(),
                    fpsLow5 = (detail?.low5Fps ?: 0.0).toFloat(),
                    temperatureMax = detail?.maxBatteryTemperature?.toFloat() ?: 25f,
                    powerAvg = detail?.avgPower?.toFloat() ?: 0f,
                    deviceResolution = detail?.deviceResolution
                )
            }

            item {
                FpsChart(
                    detail = detail,
                )
            }

            item {
                FrameTimeChart(detail = detail)
            }

            item {
                CpuLoadsChart(detail = detail)
            }
        }
    }
}


@Composable
private fun DeviceInfoContent(
    platformName: String,
    deviceName: String,
    platformVersionName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoItem(
            title = stringResource(Res.string.text_platform_type),
            icon = Res.drawable.ic_platform_device_style,
            value = platformName,
        )

        InfoItem(
            title = stringResource(Res.string.text_platform_model),
            icon = Res.drawable.ic_soc,
            value = deviceName,
        )

        InfoItem(
            title = stringResource(Res.string.text_platform_version),
            icon = Res.drawable.ic_system_version,
            value = platformVersionName,
        )
    }
}


@Composable
private fun RowScope.InfoItem(
    title: String,
    icon: DrawableResource,
    value: String,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
            style = MiuixTheme.textStyles.footnote2,
        )

        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
        )

        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.footnote1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable()
                .basicMarquee()
        )
    }
}

@Composable
private fun RecordAppInfo(
    iconPath: String?,
    recordDate: String,
    appName: String,
    appVersionInfo: String,
    fpsMax: Double,
    fpsMin: Double,
    fpsAvg: Double,
    fpsVariance: Double,
    fpsLow45: Float,
    fpsLow5: Float,
    temperatureMax: Float,
    powerAvg: Float,
    deviceResolution: String?,
) {
    Card(
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = iconPath,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = recordDate,
                    color = MiuixTheme.colorScheme.onSurface.copy(.75f),
                    style = MiuixTheme.textStyles.body2,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = buildString {
                        append(appName)
                        append("-$appVersionInfo")
                        append("\n")
                        append("CROP: $deviceResolution")
                    },
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                    style = MiuixTheme.textStyles.footnote2,
                    textAlign = TextAlign.End,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 16.dp),
                maxItemsInEachRow = 4,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecordAppInfoItem(
                    title = stringResource(Res.string.text_max_high),
                    value = remember(fpsMax) { "%.1f".format(fpsMax) },
                    foot = "FPS"
                )
                RecordAppInfoItem(
                    title = stringResource(Res.string.text_min_low),
                    value = remember(fpsMin) { "%.1f".format(fpsMin) },
                    foot = "FPS"
                )
                RecordAppInfoItem(
                    title = stringResource(Res.string.text_avg),
                    value = remember(fpsAvg) { "%.1f".format(fpsAvg) },
                    foot = "FPS"
                )
                RecordAppInfoItem(
                    title = stringResource(Res.string.text_diff),
                    value = remember(fpsVariance) { "%.1f".format(fpsVariance) },
                    foot = "FPS"
                )

                RecordAppInfoItem(
                    title = "≥45FPS",
                    value = remember(fpsLow45) { "%.1f%%".format(fpsLow45 * 100f) },
                    foot = "FPS"
                )
                RecordAppInfoItem(
                    title = "5% Low",
                    value = remember(fpsLow5) { "%.1f".format(fpsLow5) },
                    foot = "FPS"
                )
                RecordAppInfoItem(
                    title = stringResource(Res.string.text_max_high),
                    value = remember(temperatureMax) { "%.1f℃".format(temperatureMax) },
                    foot = stringResource(Res.string.text_temperature)
                )
                RecordAppInfoItem(
                    title = stringResource(Res.string.text_avg),
                    value = remember(powerAvg) { powerAvg.roundToInt().formatPower() },
                    foot = stringResource(Res.string.text_power)
                )
            }
        }
    }
}

@Composable
private fun FlowRowScope.RecordAppInfoItem(
    title: String,
    value: String,
    foot: String,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface.copy(.5f),
            style = MiuixTheme.textStyles.footnote2,
        )
        Text(
            text = value,
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.body1,
        )
        Text(
            text = foot,
            color = MiuixTheme.colorScheme.onSurface.copy(.5f),
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}

@Immutable
enum class FpsChartMode {
    Temperature,
    SoCLoads,
    BatteryLevel
}

val FpsChartMode.stringResource: StringResource
    get() = when (this) {
        FpsChartMode.Temperature -> Res.string.text_temperature
        FpsChartMode.SoCLoads -> Res.string.text_soc_load
        FpsChartMode.BatteryLevel -> Res.string.text_battery_level
    }

@Composable
private fun FpsChart(
    detail: FpsRecordDetailAggregate?,
) {
    var fpsChartMode by rememberSaveable { mutableStateOf(FpsChartMode.Temperature) }
    val primaryColor = MiuixTheme.colorScheme.primary.copy(.5f)
    val secondaryOneColor = Color(0x8CFF8A65)
    val secondaryTwoColor = Color(0x8C4FC3F7)
    val secondaryThreeColor = Color(0x8CBA68C8)
    val xAxis = remember(detail) {
        LineChartXAxisData(
            dataSet = detail?.fpsSamples?.indices?.map { (it.toLong() * 1000).formatElapsedTime() } ?: emptyList()
        )
    }
    val chartData = remember(detail, fpsChartMode, primaryColor) {
        buildChartData(
            detail = detail,
            mode = fpsChartMode,
            primaryColor = primaryColor,
            secondaryOneColor = secondaryOneColor,
            secondaryTwoColor = secondaryTwoColor,
            secondaryThreeColor = secondaryThreeColor,
        )
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row {
            SmallTitle(text = "FPS")
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(Rectangle.copy(cornerRadius = 4.dp))
                    .clickable {
                        val entries = FpsChartMode.entries
                        val mode = fpsChartMode
                        val nextIndex = (mode.ordinal + 1).let {
                            if (it !in entries.indices) 0 else it
                        }
                        fpsChartMode = entries[nextIndex]
                    }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(fpsChartMode.stringResource),
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                    ),
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f)
                )

                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary.copy(.75f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (chartData.isNotEmpty() && xAxis.dataSet.isNotEmpty()) {
            SmoothLineChart(
                modifier = Modifier.fillMaxWidth()
                    .height(250.dp),
                xAxis = xAxis,
                data = chartData,
                axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f),
                tickTextColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
                lineWidth = 1.dp,
                showGrid = true,
                gridColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .08f),
                textStyle = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                ),
                showAffix = false,
            )
        } else {
            Spacer(
                modifier = Modifier.fillMaxWidth()
                    .height(250.dp)
            )
        }

        ChartColorIndicator(chartData, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun ChartColorIndicator(
    chartData: List<LineChartData>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chartData.onEach {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.size(16.dp)
                        .clip(Rectangle.copy(4.dp))
                        .background(it.color)
                )

                Text(
                    text = it.name,
                    style = MiuixTheme.textStyles.footnote2,
                    color = it.color
                )
            }
        }
    }
}

private fun buildChartData(
    detail: FpsRecordDetailAggregate?,
    mode: FpsChartMode,
    primaryColor: Color,
    secondaryOneColor: Color,
    secondaryTwoColor: Color,
    secondaryThreeColor: Color,
): List<LineChartData> {
    if (detail == null || detail.fpsSamples.isEmpty()) {
        return emptyList()
    }
    val fpsData = LineChartData(
        name = "FPS",
        suffix = "",
        dataSet = detail.fpsSamples.map { it.toFloat() },
        color = primaryColor,
        axisType = LineChartAxisType.Primary,
    )
    return when (mode) {
        FpsChartMode.Temperature -> listOf(
            fpsData,
            LineChartData(
                name = "Temperature",
                suffix = "℃",
                dataSet = detail.batteryTemperatureSamples.map { it.toFloat() },
                color = secondaryOneColor,
                axisType = LineChartAxisType.Secondary,
            )
        )

        FpsChartMode.SoCLoads -> listOf(
            fpsData,
            LineChartData(
                name = "CPU Load",
                suffix = "%",
                dataSet = detail.cpuLoadSamples.map { (it[-1] ?: 0.0).toFloat() },
                color = secondaryOneColor,
                axisType = LineChartAxisType.Secondary,
            ),
            LineChartData(
                name = "GPU Load",
                suffix = "%",
                dataSet = detail.gpuLoadSamples.toSizedFloatList(detail.fpsSamples.size),
                color = secondaryTwoColor,
                axisType = LineChartAxisType.Secondary,
            ),
            LineChartData(
                name = "CPU Temp",
                suffix = "℃",
                dataSet = detail.cpuTemperatureSamples.map { it.toFloat() },
                color = secondaryThreeColor,
                axisType = LineChartAxisType.Secondary,
            ),
        )

        FpsChartMode.BatteryLevel -> listOf(
            fpsData,
            LineChartData(
                name = "Battery",
                suffix = "%",
                dataSet = detail.batteryLevelSamples.map { it.toFloat() },
                color = secondaryOneColor,
                axisType = LineChartAxisType.Secondary,
            )
        )
    }
}

private fun List<Double>.toSizedFloatList(size: Int): List<Float> {
    if (isEmpty()) {
        return List(size) { 0f }
    }
    if (this.size == size) {
        return map { it.toFloat() }
    }
    return buildList(size) {
        repeat(size) { index ->
            add(getOrElse(index) { last() })
        }
    }
}

@Composable
private fun FrameTimeChart(
    detail: FpsRecordDetailAggregate?,
) {
    val primary = MiuixTheme.colorScheme.primary.copy(alpha = .5f)

    val frameData = remember(detail) {
        detail?.frameTimeSamples?.map { it.toFloat() } ?: emptyList()
    }
    val xAxis = remember(detail) {
        LineChartXAxisData(
            dataSet = frameData.indices.map { (it.toLong() * 1000).formatElapsedTime() }
        )
    }
    val chartData = remember(frameData) {
        LineChartData(
            name = "Frame Time",
            suffix = "ms",
            dataSet = frameData,
            color = primary,
            axisType = LineChartAxisType.Primary,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SmallTitle(text = stringResource(Res.string.text_frame_time))
        Spacer(modifier = Modifier.height(4.dp))
        if (frameData.isNotEmpty()) {
            VerticalBarChart(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp)
                    .height(220.dp),
                xAxis = xAxis,
                data = chartData,
                axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f),
                tickTextColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
                showGrid = true,
                gridColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .08f),
                textStyle = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                ),
                showAffix = false,
                barStyleType = VerticalBarChartStyleType.Stroke,
            )
        } else {
            Spacer(
                modifier = Modifier.fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}

@Suppress("MutableCollectionMutableState")
@Composable
private fun CpuLoadsChart(
    detail: FpsRecordDetailAggregate?,
) {
    val primary = MiuixTheme.colorScheme.primary

    var loadOptionsPopState by remember { mutableStateOf(false) }
    val cpuFrequencyUtil = remember { CpuFrequencyUtil() }
    var loadOption by rememberSaveable {
        mutableStateOf(LinkedHashMap<Int, Boolean>())
    }
    var clusterInfo by rememberSaveable { mutableStateOf(emptyList<List<Int>>()) }
    val xAxis = remember(detail) {
        LineChartXAxisData(
            dataSet = detail?.cpuLoadSamples?.indices?.map { (it.toLong() * 1000).formatElapsedTime() }
                ?: emptyList()
        )
    }
    val clusterColors = remember {
        listOf(
            primary.copy(alpha = .5f),
            Color(0x8CFF8A65),
            Color(0x8C4FC3F7),
            Color(0x8CBA68C8),
            Color(0x8C81C784),
            Color(0x8CFFD54F),
            Color(0x8C64B5F6),
            Color(0x8CA1887F),
            Color(0x8C90A4AE),
            Color(0x8C7986CB),
            Color(0x8C4DB6AC),
            Color(0x8CDCE775),
            Color(0x8CFFB74D),
            Color(0x8CE57373),
            Color(0x8CF06292),
            Color(0x8C9575CD),
            Color(0x8CAED581),
            Color(0x8C4DD0E1),
            Color(0x8CFF8F00),
            Color(0x8CB0BEC5),
        )
    }
    val chartData = remember(detail, clusterInfo, loadOption, clusterColors) {
        buildCpuLoadChartData(
            detail = detail,
            clusterInfo = clusterInfo,
            loadOption = loadOption,
            colors = clusterColors,
        )
    }

    LaunchedEffect(Unit) {
        clusterInfo = cpuFrequencyUtil.getClusterInfo().map { it.map { it.toInt() } }
        if (loadOption.isEmpty()) {
            loadOption = LinkedHashMap<Int, Boolean>().apply {
                put(-1, true)
                repeat(clusterInfo.size) { clusterIndex ->
                    put(clusterIndex, true)
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row {
            SmallTitle(text = stringResource(Res.string.text_cpu_loads))
            Spacer(modifier = Modifier.weight(1f))
            Box {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(Rectangle.copy(cornerRadius = 4.dp))
                        .clickable {
                            loadOptionsPopState = true
                        }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.text_cpu_loads_source),
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                        ),
                        color = MiuixTheme.colorScheme.onSurface.copy(.31f)
                    )

                    Icon(
                        imageVector = MiuixIcons.ListView,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = primary.copy(.75f)
                    )
                }

                OverlayListPopup(
                    show = loadOptionsPopState,
                    alignment = PopupPositionProvider.Align.End,
                    onDismissRequest = { loadOptionsPopState = false } // 关闭弹窗菜单
                ) {
                    ListPopupColumn {
                        loadOption.forEach { (index, enabled) ->
                            DropdownImpl(
                                text = when (index) {
                                    -1 -> stringResource(Res.string.text_cpu_load_total)
                                    else -> clusterInfo.getOrNull(index)
                                        ?.toCpuClusterLabel()
                                        ?: "Cpu $index"
                                },
                                optionSize = loadOption.size,
                                isSelected = enabled,
                                index = index,
                                onSelectedIndexChange = {
                                    loadOption = loadOption.toggleCpuLoadOption(index)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        if (chartData.isNotEmpty() && xAxis.dataSet.isNotEmpty()) {
            SmoothLineChart(
                modifier = Modifier.fillMaxWidth()
                    .height(250.dp),
                xAxis = xAxis,
                data = chartData,
                axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f),
                tickTextColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
                lineWidth = 1.dp,
                showGrid = true,
                gridColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .08f),
                textStyle = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                ),
                showAffix = false,
            )
        } else {
            Spacer(
                modifier = Modifier.fillMaxWidth()
                    .height(250.dp)
            )
        }

        ChartColorIndicator(chartData, modifier = Modifier.padding(bottom = 16.dp))
    }
}

private fun buildCpuLoadChartData(
    detail: FpsRecordDetailAggregate?,
    clusterInfo: List<List<Int>>,
    loadOption: Map<Int, Boolean>,
    colors: List<Color>,
): List<LineChartData> {
    val cpuLoadSamples = detail?.cpuLoadSamples ?: return emptyList()
    if (cpuLoadSamples.isEmpty()) {
        return emptyList()
    }

    val result = mutableListOf<LineChartData>()
    if (loadOption[-1] == true) {
        result += LineChartData(
            name = "Total",
            suffix = "%",
            dataSet = cpuLoadSamples.map { (it[-1] ?: 0.0).toFloat() },
            color = colors.getOrElse(0) { Color.Gray },
            axisType = LineChartAxisType.Primary,
        )
    }

    clusterInfo.forEachIndexed { clusterIndex, cores ->
        if (loadOption[clusterIndex] != true) return@forEachIndexed
        val color = colors.getOrElse(clusterIndex + 1) { colors.lastOrNull() ?: Color.Gray }
        result += LineChartData(
            name = cores.toCpuClusterLabel(),
            suffix = "%",
            dataSet = cpuLoadSamples.map { sample ->
                cores.mapNotNull { core -> sample[core] }
                    .average()
                    .takeIf { !it.isNaN() }
                    ?.toFloat()
                    ?: 0f
            },
            color = color,
            axisType = LineChartAxisType.Primary,
        )
    }
    return result
}

private fun List<Int>.toCpuClusterLabel(): String {
    if (isEmpty()) return "CPU"
    if (size == 1) return "CPU ${minOrNull() ?: -1}"
    val start = minOrNull() ?: return "CPU"
    val end = maxOrNull() ?: return "CPU $start"
    return "CPU $start-$end"
}

private fun Map<Int, Boolean>.toggleCpuLoadOption(index: Int): LinkedHashMap<Int, Boolean> {
    val current = this[index] ?: false
    if (current && values.count { it } <= 1) {
        return LinkedHashMap(this)
    }
    return LinkedHashMap(this).apply {
        this[index] = !current
    }
}
