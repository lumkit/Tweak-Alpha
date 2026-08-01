package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * App 图标条上的一段前台占用，相对会话时间轴用 [startRatio]..[endRatio]（0..1）。
 */
@Immutable
data class AppStripSegment(
    val packageName: String,
    val startRatio: Float,
    val endRatio: Float,
    val iconPath: String?,
)

/**
 * 电量 % 平滑曲线 + 下方按时段对齐的 App 图标条。
 * [appSegments] 为空时只绘制曲线。
 */
@Composable
fun BatteryLevelAppStripChart(
    levelSeries: LineChartData,
    xAxis: LineChartXAxisData,
    appSegments: List<AppStripSegment>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 200.dp,
    stripHeight: Dp = 36.dp,
    iconSize: Dp = 24.dp,
    axisColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = .16f),
    tickTextColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f),
    gridColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = .08f),
    textStyle: TextStyle = MiuixTheme.textStyles.footnote2.copy(
        fontSize = 10.sp,
        lineHeight = 10.sp,
    ),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (levelSeries.dataSet.isNotEmpty() && xAxis.dataSet.isNotEmpty()) {
            SmoothLineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                xAxis = xAxis,
                data = listOf(levelSeries),
                axisColor = axisColor,
                tickTextColor = tickTextColor,
                lineWidth = 1.dp,
                showGrid = true,
                gridColor = gridColor,
                textStyle = textStyle,
                showAffix = true,
            )
        } else {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
            )
        }

        if (appSegments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AppIconStrip(
                segments = appSegments,
                iconSize = iconSize,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stripHeight),
            )
        }
    }
}

@Composable
private fun AppIconStrip(
    segments: List<AppStripSegment>,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val placeholderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    BoxWithConstraints(modifier = modifier) {
        segments.forEach { segment ->
            val mid = ((segment.startRatio + segment.endRatio) / 2f).coerceIn(0f, 1f)
            val x = maxWidth * mid - iconSize / 2
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(placeholderColor),
            ) {
                val path = segment.iconPath?.takeIf { it.isNotBlank() }
                if (path != null) {
                    AsyncImage(
                        model = path,
                        contentDescription = segment.packageName,
                        modifier = Modifier
                            .size(iconSize)
                            .clip(CircleShape),
                    )
                }
            }
        }
    }
}
