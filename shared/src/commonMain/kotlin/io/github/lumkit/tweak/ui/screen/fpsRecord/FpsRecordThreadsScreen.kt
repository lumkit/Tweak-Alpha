package io.github.lumkit.tweak.ui.screen.fpsRecord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.LineChartAxisType
import io.github.lumkit.tweak.common.component.LineChartData
import io.github.lumkit.tweak.common.component.LineChartXAxisData
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.SmoothLineChart
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.formatElapsedTime
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_fps_record_thread_loads
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_thread_load_avg_max
import tweak_alpha.shared.generated.resources.text_thread_loads_empty

private const val MAX_THREAD_CARDS = 50

@Composable
fun FpsRecordThreadsScreen(
    sessionId: Long,
    viewModel: FpsRecordDetailViewModel = viewModel(key = "sessionId-$sessionId") {
        FpsRecordDetailViewModel(sessionId)
    },
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backdrop = rememberLayerBackdropColor()
    val direction = LocalLayoutDirection.current
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val chartColor = MiuixTheme.colorScheme.primary.copy(alpha = .75f)

    val threadCards by produceState(
        initialValue = emptyList<ThreadLoadCardModel>(),
        detail,
        chartColor,
    ) {
        value = withContext(Dispatchers.Default) {
            buildThreadLoadCards(
                samples = detail?.threadLoadSamples.orEmpty(),
                chartColor = chartColor,
            )
        }
    }

    val xAxis by produceState(initialValue = LineChartXAxisData(emptyList()), detail) {
        value = withContext(Dispatchers.Default) {
            LineChartXAxisData(
                dataSet = detail?.threadLoadSamples?.indices
                    ?.map { (it.toLong() * 1000).formatElapsedTime() }
                    ?: emptyList(),
            )
        }
    }

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.text_fps_record_thread_loads),
                    subTitle = listOfNotNull(
                        detail?.appName?.takeIf { it.isNotBlank() },
                        detail?.appPackageName?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
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
            floatingToolbarPosition = ToolbarPosition.BottomEnd,
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical()
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    end = padding.calculateEndPadding(direction) + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (threadCards.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.text_thread_loads_empty),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurface.copy(.45f),
                            )
                        }
                    }
                } else {
                    items(
                        items = threadCards,
                        key = { it.key },
                    ) { card ->
                        ThreadLoadCard(
                            model = card,
                            xAxis = xAxis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadLoadCard(
    model: ThreadLoadCardModel,
    xAxis: LineChartXAxisData,
) {
    val avgMaxText = stringResource(Res.string.text_thread_load_avg_max).format(model.avgLoad,model.maxLoad)
    val chartData = remember(model) {
        listOf(
            LineChartData(
                name = model.name,
                suffix = "%",
                dataSet = model.dataSet,
                color = model.color,
                axisType = LineChartAxisType.Primary,
            )
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallTitle(
                    text = model.name,
                    modifier = Modifier.weight(1f, fill = false),
                )
                model.tid?.let { tid ->
                    Text(
                        text = tid.toString(),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurface.copy(.4f),
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Text(
                text = avgMaxText,
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                ),
                color = MiuixTheme.colorScheme.onSurface.copy(.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        if (chartData.isNotEmpty() &&
            xAxis.dataSet.isNotEmpty() &&
            chartData.all { it.dataSet.size == xAxis.dataSet.size }
        ) {
            SmoothLineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, bottom = 12.dp)
                    .height(140.dp),
                xAxis = xAxis,
                data = chartData,
                axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f),
                tickTextColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
                lineWidth = 1.5.dp,
                showGrid = true,
                gridColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .08f),
                textStyle = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                ),
                showAffix = false,
            )
        } else {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

private data class ThreadLoadCardModel(
    val key: String,
    val name: String,
    val tid: Int?,
    val avgLoad: Double,
    val maxLoad: Double,
    val dataSet: List<Float>,
    val color: Color,
)

private fun buildThreadLoadCards(
    samples: List<Map<String, Double>>,
    chartColor: Color,
): List<ThreadLoadCardModel> {
    if (samples.isEmpty()) return emptyList()

    val keys = LinkedHashSet<String>()
    samples.forEach { keys.addAll(it.keys) }
    if (keys.isEmpty()) return emptyList()

    return keys.map { key ->
        val (tid, name) = parseThreadLoadKey(key)
        var sum = 0.0
        var max = 0.0
        val dataSet = ArrayList<Float>(samples.size)
        samples.forEach { sample ->
            val value = sample[key] ?: 0.0
            sum += value
            if (value > max) max = value
            dataSet += value.toFloat()
        }
        ThreadLoadCardModel(
            key = key,
            name = name,
            tid = tid,
            avgLoad = sum / samples.size,
            maxLoad = max,
            dataSet = dataSet,
            color = chartColor,
        )
    }
        .filter { it.maxLoad > 0.0 }
        .sortedByDescending { it.avgLoad }
        .take(MAX_THREAD_CARDS)
}

/**
 * 新数据格式为 `tid|name`；旧数据可能只有线程名。
 */
private fun parseThreadLoadKey(key: String): Pair<Int?, String> {
    val separatorIndex = key.indexOf('|')
    if (separatorIndex > 0) {
        val tid = key.substring(0, separatorIndex).toIntOrNull()
        if (tid != null) {
            val name = key.substring(separatorIndex + 1).ifBlank { tid.toString() }
            return tid to name
        }
    }
    return null to key
}
