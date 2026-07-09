package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

@Immutable
enum class LineChartAxisType {
    Primary,
    Secondary,
}

@Immutable
enum class VerticalBarChartStyleType {
    Fill,
    Stroke,
}

@Immutable
data class LineChartData(
    val name: String,
    val prefix: String = "",
    val suffix: String = "",
    val dataSet: List<Float>,
    val color: Color,
    val axisType: LineChartAxisType = LineChartAxisType.Primary,
)

@Immutable
data class LineChartXAxisData(
    val dataSet: List<String>,
)

@Immutable
private data class SampledLineChartData(
    val name: String,
    val prefix: String,
    val suffix: String,
    val color: Color,
    val axisType: LineChartAxisType,
    val dataSet: List<Float>,
)

@Immutable
private data class SampledXAxisData(
    val dataSet: List<String>,
)

@Immutable
private data class AxisTicks(
    val maxValue: Float,
    val tickValues: List<Float>,
)

@Composable
fun SmoothLineChart(
    xAxis: LineChartXAxisData,
    data: List<LineChartData>,
    modifier: Modifier = Modifier,
    axisLineWidth: Dp = 1.dp,
    axisColor: Color = Color(0xFF6F6F6F),
    tickTextColor: Color = Color(0xFF8F8F8F),
    lineWidth: Dp = 2.dp,
    showGrid: Boolean = true,
    gridColor: Color = Color(0x225F5F5F),
    gridLineWidth: Dp = 1.dp,
    xAxisTickCount: Int = 5,
    yAxisTickCount: Int = 5,
    textStyle: TextStyle = TextStyle.Default,
    showAffix: Boolean = true,
) {
    if (data.isEmpty() || xAxis.dataSet.isEmpty()) {
        return
    }
    data.forEach {
        require(it.name.isNotBlank()) { "Line chart data name must not be blank." }
        if (it.dataSet.size != xAxis.dataSet.size) {
            return
        }
    }

    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val maxPoints = remember(maxWidth) {
            // One representative point per pixel is enough for a line chart.
            max(2, floor(maxWidth.value).toInt())
        }
        val sampledInput = remember(xAxis, data, maxPoints) {
            sampleLineChartData(
                xAxis = xAxis,
                data = data,
                maxSamples = maxPoints,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSmoothLineChart(
                xAxis = sampledInput.first,
                data = sampledInput.second,
                textMeasurer = textMeasurer,
                density = this,
                axisLineWidth = axisLineWidth,
                axisColor = axisColor,
                tickTextColor = tickTextColor,
                lineWidth = lineWidth,
                showGrid = showGrid,
                gridColor = gridColor,
                gridLineWidth = gridLineWidth,
                xAxisTickCount = xAxisTickCount.coerceIn(1, 5),
                yAxisTickCount = yAxisTickCount.coerceIn(1, 5),
                textStyle = textStyle,
                showAffix = showAffix,
            )
        }
    }
}

@Composable
fun VerticalBarChart(
    xAxis: LineChartXAxisData,
    data: LineChartData,
    modifier: Modifier = Modifier,
    axisLineWidth: Dp = 1.dp,
    axisColor: Color = Color(0xFF6F6F6F),
    tickTextColor: Color = Color(0xFF8F8F8F),
    showGrid: Boolean = true,
    gridColor: Color = Color(0x225F5F5F),
    gridLineWidth: Dp = 1.dp,
    xAxisTickCount: Int = 5,
    yAxisTickCount: Int = 10,
    textStyle: TextStyle = TextStyle.Default,
    showAffix: Boolean = true,
    barStyleType: VerticalBarChartStyleType = VerticalBarChartStyleType.Fill,
    barBorderColor: Color = Color.Unspecified,
    barBorderWidth: Dp = 1.dp,
) {
    require(data.name.isNotBlank()) { "Bar chart data name must not be blank." }
    if (xAxis.dataSet.isEmpty() || data.dataSet.isEmpty()) {
        return
    }
    require(data.axisType == LineChartAxisType.Primary) {
        "VerticalBarChart only supports Primary axis data."
    }
    if (data.dataSet.size != xAxis.dataSet.size) {
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val resolvedBarBorderColor = if (barBorderColor == Color.Unspecified) {
        MiuixTheme.colorScheme.primary.copy(alpha = .5f)
    } else {
        barBorderColor
    }

    BoxWithConstraints(modifier = modifier) {
        val maxBars = remember(maxWidth) {
            max(2, floor(maxWidth.value).toInt())
        }
        val sampledInput = remember(xAxis, data, maxBars) {
            val sampled = sampleLineChartData(
                xAxis = xAxis,
                data = listOf(data),
                maxSamples = maxBars,
            )
            sampled.first to sampled.second.first()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVerticalBarChart(
                xAxis = sampledInput.first,
                data = sampledInput.second,
                textMeasurer = textMeasurer,
                density = this,
                axisLineWidth = axisLineWidth,
                axisColor = axisColor,
                tickTextColor = tickTextColor,
                showGrid = showGrid,
                gridColor = gridColor,
                gridLineWidth = gridLineWidth,
                xAxisTickCount = xAxisTickCount.coerceIn(1, 5),
                yAxisTickCount = yAxisTickCount.coerceIn(1, 10),
                textStyle = textStyle,
                showAffix = showAffix,
                barStyleType = barStyleType,
                barBorderColor = resolvedBarBorderColor,
                barBorderWidth = barBorderWidth,
            )
        }
    }
}

private fun sampleLineChartData(
    xAxis: LineChartXAxisData,
    data: List<LineChartData>,
    maxSamples: Int,
): Pair<SampledXAxisData, List<SampledLineChartData>> {
    val sourceSize = xAxis.dataSet.size
    if (sourceSize <= maxSamples) {
        return SampledXAxisData(xAxis.dataSet) to data.map {
            SampledLineChartData(
                name = it.name,
                prefix = it.prefix,
                suffix = it.suffix,
                color = it.color,
                axisType = it.axisType,
                dataSet = it.dataSet,
            )
        }
    }

    val sampledLabels = ArrayList<String>(maxSamples)
    val sampledValues = data.map { ArrayList<Float>(maxSamples) }
    for (sampleIndex in 0 until maxSamples) {
        val start = floor(sampleIndex * sourceSize / maxSamples.toFloat()).toInt()
        val end = floor((sampleIndex + 1) * sourceSize / maxSamples.toFloat()).toInt()
            .coerceAtLeast(start + 1)
            .coerceAtMost(sourceSize)
        val center = ((start + end - 1) / 2).coerceIn(0, sourceSize - 1)
        sampledLabels += xAxis.dataSet[center]
        data.forEachIndexed { index, line ->
            val bucket = line.dataSet.subList(start, end)
            sampledValues[index] += bucket.average().toFloat()
        }
    }

    return SampledXAxisData(sampledLabels) to data.mapIndexed { index, line ->
        SampledLineChartData(
            name = line.name,
            prefix = line.prefix,
            suffix = line.suffix,
            color = line.color,
            axisType = line.axisType,
            dataSet = sampledValues[index],
        )
    }
}

private fun DrawScope.drawSmoothLineChart(
    xAxis: SampledXAxisData,
    data: List<SampledLineChartData>,
    textMeasurer: TextMeasurer,
    density: Density,
    axisLineWidth: Dp,
    axisColor: Color,
    tickTextColor: Color,
    lineWidth: Dp,
    showGrid: Boolean,
    gridColor: Color,
    gridLineWidth: Dp,
    xAxisTickCount: Int,
    yAxisTickCount: Int,
    textStyle: TextStyle,
    showAffix: Boolean,
) {
    val primaryData = data.filter { it.axisType == LineChartAxisType.Primary }
    val secondaryData = data.filter { it.axisType == LineChartAxisType.Secondary }
    val primaryTicks = buildAxisTicks(primaryData, yAxisTickCount)
    val secondaryTicks = buildAxisTicks(secondaryData, yAxisTickCount)
    val xTickIndexes = buildXAxisTickIndexes(xAxis.dataSet.size, xAxisTickCount)

    val leftFormatter = primaryData.firstOrNull()?.let { { value: Float ->
        formatAxisValue(value, it.prefix, it.suffix, showAffix = showAffix)
    } } ?: { value: Float -> formatAxisValue(value, "", "") }
    val rightFormatter = secondaryData.firstOrNull()?.let { { value: Float ->
        formatAxisValue(value, it.prefix, it.suffix, showAffix = showAffix)
    } } ?: { value: Float -> formatAxisValue(value, "", "", showAffix = showAffix) }

    val leftLabelWidth = (primaryTicks.tickValues + 0f).maxOfOrNull { tick ->
        textMeasurer.measure(
            text = leftFormatter(tick),
            style = textStyle,
        ).size.width
    } ?: 0
    val rightLabelWidth = if (secondaryData.isEmpty()) {
        0
    } else {
        (secondaryTicks.tickValues + 0f).maxOfOrNull { tick ->
            textMeasurer.measure(
                text = rightFormatter(tick),
                style = textStyle,
            ).size.width
        } ?: 0
    }
    val xLabelHeight = xTickIndexes.maxOfOrNull { index ->
        textMeasurer.measure(
            text = xAxis.dataSet[index],
            style = textStyle,
        ).size.height
    } ?: 0

    val labelSpacingPx = with(density) { 8.dp.toPx() }
    val leftPadding = leftLabelWidth + labelSpacingPx * 2
    val rightPadding = if (secondaryData.isEmpty()) {
        labelSpacingPx
    } else {
        rightLabelWidth + labelSpacingPx * 2
    }
    val topPadding = labelSpacingPx
    val bottomPadding = xLabelHeight + labelSpacingPx * 2

    val plotLeft = leftPadding
    val plotRight = size.width - rightPadding
    val plotTop = topPadding
    val plotBottom = size.height - bottomPadding
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
    val axisStroke = with(density) { axisLineWidth.toPx() }
    val lineStroke = with(density) { lineWidth.toPx() }
    val gridStroke = with(density) { gridLineWidth.toPx() }

    if (plotWidth <= 1f || plotHeight <= 1f) return

    if (showGrid) {
        val dashPathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(8f, 8f),
            phase = 0f,
        )
        drawVerticalGrid(
            xTickIndexes = xTickIndexes,
            plotLeft = plotLeft,
            plotTop = plotTop,
            plotBottom = plotBottom,
            plotWidth = plotWidth,
            pointCount = xAxis.dataSet.size,
            color = gridColor,
            strokeWidth = gridStroke,
            pathEffect = dashPathEffect,
        )
        drawHorizontalGrid(
            ticks = primaryTicks.tickValues,
            maxValue = primaryTicks.maxValue,
            plotLeft = plotLeft,
            plotRight = plotRight,
            plotTop = plotTop,
            plotHeight = plotHeight,
            color = gridColor,
            strokeWidth = gridStroke,
            pathEffect = dashPathEffect,
        )
        if (secondaryData.isNotEmpty()) {
            drawHorizontalGrid(
                ticks = secondaryTicks.tickValues,
                maxValue = secondaryTicks.maxValue,
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = plotTop,
                plotHeight = plotHeight,
                color = gridColor,
                strokeWidth = gridStroke,
                pathEffect = dashPathEffect,
            )
        }
    }

    drawLine(
        color = axisColor,
        start = Offset(plotLeft, plotBottom),
        end = Offset(plotRight, plotBottom),
        strokeWidth = axisStroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = axisColor,
        start = Offset(plotLeft, plotTop),
        end = Offset(plotLeft, plotBottom),
        strokeWidth = axisStroke,
        cap = StrokeCap.Round,
    )
    if (secondaryData.isNotEmpty()) {
        drawLine(
            color = axisColor,
            start = Offset(plotRight, plotTop),
            end = Offset(plotRight, plotBottom),
            strokeWidth = axisStroke,
            cap = StrokeCap.Round,
        )
    }

    drawAxisTickLabels(
        ticks = primaryTicks.tickValues,
        maxValue = primaryTicks.maxValue,
        plotTop = plotTop,
        plotHeight = plotHeight,
        formatter = leftFormatter,
        textMeasurer = textMeasurer,
        textStyle = textStyle,
        textColor = tickTextColor,
        anchorX = plotLeft - labelSpacingPx,
        alignRight = true,
    )
    if (secondaryData.isNotEmpty()) {
        drawAxisTickLabels(
            ticks = secondaryTicks.tickValues,
            maxValue = secondaryTicks.maxValue,
            plotTop = plotTop,
            plotHeight = plotHeight,
            formatter = rightFormatter,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            textColor = tickTextColor,
            anchorX = plotRight + labelSpacingPx,
            alignRight = false,
        )
    }
    drawXAxisLabels(
        indexes = xTickIndexes,
        labels = xAxis.dataSet,
        plotLeft = plotLeft,
        plotBottom = plotBottom,
        plotWidth = plotWidth,
        pointCount = xAxis.dataSet.size,
        textMeasurer = textMeasurer,
        textStyle = textStyle,
        textColor = tickTextColor,
        topPadding = labelSpacingPx,
    )

    data.forEach { line ->
        val axisMax = when (line.axisType) {
            LineChartAxisType.Primary -> primaryTicks.maxValue
            LineChartAxisType.Secondary -> secondaryTicks.maxValue
        }
        val points = line.dataSet.mapIndexed { index, value ->
            val x = plotLeft + index.toChartX(plotWidth, line.dataSet.size)
            val y = plotBottom - value.toChartY(axisMax, plotHeight)
            Offset(x, y)
        }
        if (points.isEmpty()) return@forEach
        if (points.size == 1) {
            drawCircle(
                color = line.color,
                radius = lineStroke,
                center = points.first(),
            )
        } else {
            drawPath(
                path = buildSmoothPath(points),
                color = line.color,
                style = Stroke(width = lineStroke, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawVerticalBarChart(
    xAxis: SampledXAxisData,
    data: SampledLineChartData,
    textMeasurer: TextMeasurer,
    density: Density,
    axisLineWidth: Dp,
    axisColor: Color,
    tickTextColor: Color,
    showGrid: Boolean,
    gridColor: Color,
    gridLineWidth: Dp,
    xAxisTickCount: Int,
    yAxisTickCount: Int,
    textStyle: TextStyle,
    showAffix: Boolean,
    barStyleType: VerticalBarChartStyleType,
    barBorderColor: Color,
    barBorderWidth: Dp,
) {
    val ticks = buildAxisTicks(listOf(data), yAxisTickCount)
    val xTickIndexes = buildXAxisTickIndexes(xAxis.dataSet.size, xAxisTickCount)
    val formatter: (Float) -> String = { value ->
        formatAxisValue(value, data.prefix, data.suffix, showAffix = showAffix)
    }

    val leftLabelWidth = (ticks.tickValues + 0f).maxOfOrNull { tick ->
        textMeasurer.measure(
            text = formatter(tick),
            style = textStyle,
        ).size.width
    } ?: 0
    val xLabelHeight = xTickIndexes.maxOfOrNull { index ->
        textMeasurer.measure(
            text = xAxis.dataSet[index],
            style = textStyle,
        ).size.height
    } ?: 0

    val labelSpacingPx = with(density) { 8.dp.toPx() }
    val leftPadding = leftLabelWidth + labelSpacingPx * 2
    val rightPadding = labelSpacingPx
    val topPadding = labelSpacingPx
    val bottomPadding = xLabelHeight + labelSpacingPx * 2

    val plotLeft = leftPadding
    val plotRight = size.width - rightPadding
    val plotTop = topPadding
    val plotBottom = size.height - bottomPadding
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
    val axisStroke = with(density) { axisLineWidth.toPx() }
    val gridStroke = with(density) { gridLineWidth.toPx() }
    val borderStroke = with(density) { barBorderWidth.toPx() }

    if (plotWidth <= 1f || plotHeight <= 1f) return

    if (showGrid) {
        val dashPathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(8f, 8f),
            phase = 0f,
        )
        drawBarVerticalGrid(
            xTickIndexes = xTickIndexes,
            plotLeft = plotLeft,
            plotTop = plotTop,
            plotBottom = plotBottom,
            plotWidth = plotWidth,
            itemCount = xAxis.dataSet.size,
            color = gridColor,
            strokeWidth = gridStroke,
            pathEffect = dashPathEffect,
        )
        drawHorizontalGrid(
            ticks = ticks.tickValues,
            maxValue = ticks.maxValue,
            plotLeft = plotLeft,
            plotRight = plotRight,
            plotTop = plotTop,
            plotHeight = plotHeight,
            color = gridColor,
            strokeWidth = gridStroke,
            pathEffect = dashPathEffect,
        )
    }

    drawLine(
        color = axisColor,
        start = Offset(plotLeft, plotBottom),
        end = Offset(plotRight, plotBottom),
        strokeWidth = axisStroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = axisColor,
        start = Offset(plotLeft, plotTop),
        end = Offset(plotLeft, plotBottom),
        strokeWidth = axisStroke,
        cap = StrokeCap.Round,
    )

    drawAxisTickLabels(
        ticks = ticks.tickValues,
        maxValue = ticks.maxValue,
        plotTop = plotTop,
        plotHeight = plotHeight,
        formatter = formatter,
        textMeasurer = textMeasurer,
        textStyle = textStyle,
        textColor = tickTextColor,
        anchorX = plotLeft - labelSpacingPx,
        alignRight = true,
    )
    drawBarXAxisLabels(
        indexes = xTickIndexes,
        labels = xAxis.dataSet,
        plotLeft = plotLeft,
        plotBottom = plotBottom,
        plotWidth = plotWidth,
        itemCount = xAxis.dataSet.size,
        textMeasurer = textMeasurer,
        textStyle = textStyle,
        textColor = tickTextColor,
        topPadding = labelSpacingPx,
    )

    val slotWidth = plotWidth / xAxis.dataSet.size.coerceAtLeast(1)
    data.dataSet.forEachIndexed { index, value ->
        val barHeight = value.toChartY(ticks.maxValue, plotHeight)
        val left = plotLeft + index * slotWidth
        val right = (left + slotWidth).coerceAtMost(plotRight)
        val top = plotBottom - barHeight
        when (barStyleType) {
            VerticalBarChartStyleType.Fill -> {
                drawSegmentedBar(
                    left = left,
                    right = right,
                    top = top,
                    bottom = plotBottom,
                    fillColor = data.color,
                    strokeColor = barBorderColor,
                    strokeWidth = borderStroke,
                    fill = true,
                )
            }

            VerticalBarChartStyleType.Stroke -> {
                drawSegmentedBar(
                    left = left,
                    right = right,
                    top = top,
                    bottom = plotBottom,
                    fillColor = Color.Transparent,
                    strokeColor = barBorderColor,
                    strokeWidth = borderStroke,
                    fill = false,
                )
            }
        }
    }
}

private fun DrawScope.drawSegmentedBar(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float,
    fill: Boolean,
    divisions: Int = 6,
) {
    val safeRight = right.coerceAtLeast(left + 1f)
    val safeTop = top.coerceAtMost(bottom)
    val width = safeRight - left
    if (width <= 0f) return

    if (fill) {
        drawRect(
            color = fillColor,
            topLeft = Offset(left, safeTop),
            size = Size(width = width, height = (bottom - safeTop).coerceAtLeast(0f)),
        )
    }

    if (strokeWidth <= 0f) return

    val step = width / divisions
    // Only keep the outer contour and the segmented top edge.
    drawLine(
        color = strokeColor,
        start = Offset(left, bottom),
        end = Offset(left, safeTop),
        strokeWidth = strokeWidth,
    )
    for (index in 1..divisions) {
        val previousX = (left + (index - 1) * step).coerceAtMost(safeRight)
        val x = (left + index * step).coerceAtMost(safeRight)
        drawLine(
            color = strokeColor,
            start = Offset(previousX, safeTop),
            end = Offset(x, safeTop),
            strokeWidth = strokeWidth,
        )
    }
    drawLine(
        color = strokeColor,
        start = Offset(safeRight, bottom),
        end = Offset(safeRight, safeTop),
        strokeWidth = strokeWidth,
    )
}

private fun DrawScope.drawVerticalGrid(
    xTickIndexes: List<Int>,
    plotLeft: Float,
    plotTop: Float,
    plotBottom: Float,
    plotWidth: Float,
    pointCount: Int,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect,
) {
    xTickIndexes.forEach { index ->
        val x = plotLeft + index.toChartX(plotWidth, pointCount)
        if (x <= plotLeft || x >= plotLeft + plotWidth) {
            return@forEach
        }
        drawLine(
            color = color,
            start = Offset(x, plotTop),
            end = Offset(x, plotBottom),
            strokeWidth = strokeWidth,
            pathEffect = pathEffect,
        )
    }
}

private fun DrawScope.drawHorizontalGrid(
    ticks: List<Float>,
    maxValue: Float,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotHeight: Float,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect,
) {
    ticks.forEach { tick ->
        val y = plotTop + plotHeight - tick.toChartY(maxValue, plotHeight)
        if (y >= plotTop + plotHeight || y <= plotTop) {
            return@forEach
        }
        drawLine(
            color = color,
            start = Offset(plotLeft, y),
            end = Offset(plotRight, y),
            strokeWidth = strokeWidth,
            pathEffect = pathEffect,
        )
    }
}

private fun DrawScope.drawBarVerticalGrid(
    xTickIndexes: List<Int>,
    plotLeft: Float,
    plotTop: Float,
    plotBottom: Float,
    plotWidth: Float,
    itemCount: Int,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect,
) {
    if (itemCount <= 0) return
    val slotWidth = plotWidth / itemCount
    xTickIndexes.forEach { index ->
        val x = plotLeft + (index + .5f) * slotWidth
        if (x <= plotLeft || x >= plotLeft + plotWidth) {
            return@forEach
        }
        drawLine(
            color = color,
            start = Offset(x, plotTop),
            end = Offset(x, plotBottom),
            strokeWidth = strokeWidth,
            pathEffect = pathEffect,
        )
    }
}

private fun DrawScope.drawAxisTickLabels(
    ticks: List<Float>,
    maxValue: Float,
    plotTop: Float,
    plotHeight: Float,
    formatter: (Float) -> String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    textColor: Color,
    anchorX: Float,
    alignRight: Boolean,
) {
    val originLayout = textMeasurer.measure(
        text = formatter(0f),
        style = textStyle,
    )
    drawText(
        textLayoutResult = originLayout,
        color = textColor,
        topLeft = Offset(
            x = if (alignRight) anchorX - originLayout.size.width else anchorX,
            y = plotTop + plotHeight - originLayout.size.height / 2f,
        ),
    )
    ticks.forEach { tick ->
        val layout = textMeasurer.measure(
            text = formatter(tick),
            style = textStyle,
        )
        val y = plotTop + plotHeight - tick.toChartY(maxValue, plotHeight)
        drawText(
            textLayoutResult = layout,
            color = textColor,
            topLeft = Offset(
                x = if (alignRight) anchorX - layout.size.width else anchorX,
                y = y - layout.size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawXAxisLabels(
    indexes: List<Int>,
    labels: List<String>,
    plotLeft: Float,
    plotBottom: Float,
    plotWidth: Float,
    pointCount: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    textColor: Color,
    topPadding: Float,
) {
    indexes.forEach { index ->
        val layout = textMeasurer.measure(
            text = labels[index],
            style = textStyle,
        )
        val x = plotLeft + index.toChartX(plotWidth, pointCount)
        drawText(
            textLayoutResult = layout,
            color = textColor,
            topLeft = Offset(
                x = (x - layout.size.width / 2f).coerceAtLeast(0f),
                y = plotBottom + topPadding,
            ),
        )
    }
}

private fun DrawScope.drawBarXAxisLabels(
    indexes: List<Int>,
    labels: List<String>,
    plotLeft: Float,
    plotBottom: Float,
    plotWidth: Float,
    itemCount: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    textColor: Color,
    topPadding: Float,
) {
    if (itemCount <= 0) return
    val slotWidth = plotWidth / itemCount
    indexes.forEach { index ->
        val layout = textMeasurer.measure(
            text = labels[index],
            style = textStyle,
        )
        val x = plotLeft + (index + .5f) * slotWidth
        drawText(
            textLayoutResult = layout,
            color = textColor,
            topLeft = Offset(
                x = (x - layout.size.width / 2f).coerceAtLeast(0f),
                y = plotBottom + topPadding,
            ),
        )
    }
}

private fun buildAxisTicks(
    data: List<SampledLineChartData>,
    maxTickCount: Int,
): AxisTicks {
    val maxValue = data.maxOfOrNull { line ->
        line.dataSet.maxOrNull() ?: 0f
    } ?: 0f
    if (maxValue <= 0f) {
        return AxisTicks(
            maxValue = 1f,
            tickValues = listOf(1f),
        )
    }
    val interval = niceTickStep(maxValue, maxTickCount)
    val niceMax = ceil(maxValue / interval) * interval
    val tickCount = (niceMax / interval).roundToInt().coerceIn(1, maxTickCount)
    return AxisTicks(
        maxValue = niceMax,
        tickValues = (1..tickCount).map { index -> interval * index },
    )
}

private fun buildXAxisTickIndexes(pointCount: Int, maxTickCount: Int): List<Int> {
    if (pointCount <= 1) return listOf(0)
    val tickCount = pointCount.coerceAtMost(maxTickCount)
    if (tickCount == 1) return listOf(0)
    return (0 until tickCount)
        .map { tickIndex ->
            ((pointCount - 1) * tickIndex / (tickCount - 1).toFloat()).roundToInt()
        }
        .distinct()
}

private fun niceTickStep(maxValue: Float, maxTickCount: Int): Float {
    val rawStep = maxValue / maxTickCount.coerceAtLeast(1)
    if (rawStep <= 0f) return 1f
    val exponent = floor(ln(rawStep.toDouble()) / ln(10.0)).toInt()
    val magnitude = 10f.pow(exponent)
    val fraction = rawStep / magnitude
    val niceFraction = when {
        fraction <= 1f -> 1f
        fraction <= 2f -> 2f
        fraction <= 5f -> 5f
        else -> 10f
    }
    return niceFraction * magnitude
}

@Suppress("DefaultLocale")
private fun formatAxisValue(
    value: Float,
    prefix: String,
    suffix: String,
    showAffix: Boolean = true,
): String {
    val body = if ((value - value.toInt()).let { kotlin.math.abs(it) } < 0.001f) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
    if (!showAffix) {
        return body
    }
    return buildString {
        append(prefix)
        append(body)
        append(suffix)
    }
}

private fun buildSmoothPath(points: List<Offset>): Path {
    return Path().apply {
        moveTo(points.first().x, points.first().y)
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val controlX = (previous.x + current.x) / 2f
            cubicTo(
                x1 = controlX,
                y1 = previous.y,
                x2 = controlX,
                y2 = current.y,
                x3 = current.x,
                y3 = current.y,
            )
        }
    }
}

private fun Int.toChartX(plotWidth: Float, pointCount: Int): Float {
    if (pointCount <= 1) return 0f
    return this / (pointCount - 1).toFloat() * plotWidth
}

private fun Float.toChartY(maxValue: Float, plotHeight: Float): Float {
    if (maxValue <= 0f) return 0f
    return (this / maxValue).coerceIn(0f, 1f) * plotHeight
}
