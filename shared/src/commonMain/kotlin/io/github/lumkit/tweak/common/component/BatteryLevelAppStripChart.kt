package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor
import kotlin.math.max

/**
 * 某一时间槽内的单个应用耗电格子（柱内自下而上按功耗从低到高）。
 */
@Immutable
data class AppMinuteBarCell(
    val packageName: String,
    val iconPath: String?,
    val powerUw: Long,
)

/**
 * 图标槽列：落在会话时间轴上的 [centerRatio]（0..1），[cells] 已按功耗升序。
 * [minuteIndex] 为槽下标（0 until iconSlotCount）。
 */
@Immutable
data class AppMinuteBarColumn(
    val minuteIndex: Int,
    val centerRatio: Float,
    val cells: List<AppMinuteBarCell>,
)

/**
 * 电量 % 曲线 + 叠加在绘图区的应用柱：
 * 每个 X 轴区间均分 5 槽，槽宽 = 区间宽 / 5；图标 1.5dp 圆角。
 * 叠层内边距与 [SmoothLineChart] 实际轴线区域对齐。
 */
@Composable
fun BatteryLevelAppStripChart(
    levelSeries: LineChartData,
    xAxis: LineChartXAxisData,
    minuteColumns: List<AppMinuteBarColumn>,
    iconSlotCount: Int,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 200.dp,
    xAxisTickIndexes: List<Int> = emptyList(),
    axisColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f),
    tickTextColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
    gridColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = .08f),
    barTint: Color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
    textStyle: TextStyle = MiuixTheme.textStyles.footnote2.copy(
        fontSize = 10.sp,
        lineHeight = 10.sp,
    ),
) {
    val resolvedSlotCount = iconSlotCount.coerceAtLeast(1)
    val resolvedTickIndexes = xAxisTickIndexes.takeIf { it.isNotEmpty() }
    val plotPadding = rememberSmoothLineChartPlotPadding(
        xAxis = xAxis,
        data = listOf(levelSeries),
        textStyle = textStyle,
        showAffix = true,
        xAxisTickCount = resolvedTickIndexes?.size?.coerceIn(1, 6) ?: 6,
        xAxisTickIndexes = resolvedTickIndexes,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
    ) {
        if (levelSeries.dataSet.isNotEmpty() && xAxis.dataSet.isNotEmpty()) {
            SmoothLineChart(
                modifier = Modifier.fillMaxSize(),
                xAxis = xAxis,
                data = listOf(levelSeries),
                axisColor = axisColor,
                tickTextColor = tickTextColor,
                lineWidth = 1.dp,
                showGrid = true,
                gridColor = gridColor,
                textStyle = textStyle,
                showAffix = true,
                xAxisTickCount = resolvedTickIndexes?.size?.coerceIn(1, 6) ?: 6,
                xAxisTickIndexes = resolvedTickIndexes,
            )
        }

        if (minuteColumns.isNotEmpty()) {
            MinuteAppBarOverlay(
                columns = minuteColumns,
                iconSlotCount = resolvedSlotCount,
                barTint = barTint,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = plotPadding.start,
                        end = plotPadding.end,
                        top = plotPadding.top,
                        bottom = plotPadding.bottom,
                    ),
            )
        }
    }
}

@Composable
private fun MinuteAppBarOverlay(
    columns: List<AppMinuteBarColumn>,
    iconSlotCount: Int,
    barTint: Color,
    modifier: Modifier = Modifier,
) {
    val placeholderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val iconCorner = RoundedCornerShape(1.5.dp)
    BoxWithConstraints(modifier = modifier) {
        val plotWidth = maxWidth
        val plotHeight = maxHeight
        // 槽宽 = 区间宽 / 5 = 绘图宽 / iconSlotCount（与轴线时间域对齐）
        val columnWidth = plotWidth / iconSlotCount
        val iconSize = columnWidth
        val maxIcons = remember(plotHeight, iconSize) {
            max(1, floor(plotHeight / iconSize).toInt())
        }

        columns.forEach { column ->
            val slotIndex = column.minuteIndex.coerceIn(0, iconSlotCount - 1)
            val visibleCells = if (column.cells.size <= maxIcons) {
                column.cells
            } else {
                column.cells.takeLast(maxIcons)
            }
            if (visibleCells.isEmpty()) return@forEach

            val x = columnWidth * slotIndex
            val stackHeight = iconSize * visibleCells.size

            Box(
                modifier = Modifier
                    .offset(x = x, y = plotHeight - stackHeight)
                    .width(columnWidth)
                    .height(stackHeight)
                    .clip(RoundedCornerShape(topStart = 1.5.dp, topEnd = 1.5.dp))
                    .background(barTint),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    visibleCells.asReversed().forEach { cell ->
                        Box(
                            modifier = Modifier
                                .size(iconSize)
                                .clip(iconCorner)
                                .background(placeholderColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            val path = cell.iconPath?.takeIf { it.isNotBlank() }
                            if (path != null) {
                                AsyncImage(
                                    model = path,
                                    contentDescription = cell.packageName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(0.5.dp)
                                        .clip(iconCorner),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
