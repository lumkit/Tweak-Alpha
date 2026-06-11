package io.github.lumkit.tweak.common.component

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val json = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val lintChartStateSaver = object : Saver<LintChartState, String> {
    override fun restore(value: String): LintChartState {
        return LintChartState(json.decodeFromString(value))
    }

    override fun SaverScope.save(value: LintChartState): String {
        return json.encodeToString(value.snapshot())
    }
}

@Immutable
@Serializable
data class ChartState(
    @param:FloatRange(from = 0.0, to = 1.0) val progress: Float,
)

@Immutable
internal data class LintChartViewport(
    val widthPx: Float = 0f,
    val heightPx: Float = 0f,
    val chartWidthPx: Float = 0f,
    val minBarHeightPx: Float = 0f,
)

@Immutable
internal data class LintChartBarModel(
    val ratio: Float,
    val x: Float,
    val top: Float,
    val width: Float,
    val alpha: Float,
    val level: Level,
) {
    enum class Level {
        DEFAULT,
        MID,
        HIGH,
    }
}

@Immutable
internal data class LintChartRenderModel(
    val bars: List<LintChartBarModel> = emptyList(),
)

@Stable
class LintChartState internal constructor(initialHistory: List<Float> = emptyList()) {
    private val historyValues = initialHistory.toMutableList()
    private val lock = Any()
    private var viewport = LintChartViewport()
    private var scope: CoroutineScope? = null
    private var computeJob: Job? = null

    internal var renderModel by mutableStateOf(LintChartRenderModel())
        private set

    internal fun snapshot(): List<Float> = synchronized(lock) { historyValues.toList() }

    internal fun bindScope(scope: CoroutineScope) {
        this.scope = scope
        scheduleRecompute()
    }

    internal fun updateViewport(
        widthPx: Float,
        heightPx: Float,
        chartWidthPx: Float,
        minBarHeightPx: Float,
    ) {
        val newViewport = LintChartViewport(
            widthPx = widthPx,
            heightPx = heightPx,
            chartWidthPx = chartWidthPx,
            minBarHeightPx = minBarHeightPx,
        )
        if (viewport == newViewport) {
            return
        }
        viewport = newViewport
        scheduleRecompute()
    }

    fun push(state: ChartState) {
        push(state.progress)
    }

    fun push(@FloatRange(from = 0.0, to = 1.0) progress: Float) {
        synchronized(lock) {
            historyValues.add((progress * 100f).bounds(0f, 100f))
        }
        scheduleRecompute()
    }

    fun clear() {
        synchronized(lock) {
            historyValues.clear()
        }
        scheduleRecompute()
    }

    private fun scheduleRecompute() {
        val currentScope = scope ?: return
        val currentViewport = viewport
        computeJob?.cancel()
        computeJob = currentScope.launch {
            val historySnapshot = synchronized(lock) { historyValues.toList() }
            val renderModel = withContext(Dispatchers.Default) {
                buildRenderModel(historySnapshot, currentViewport)
            }
            this@LintChartState.renderModel = renderModel
        }
    }

    private fun buildRenderModel(
        history: List<Float>,
        viewport: LintChartViewport,
    ): LintChartRenderModel {
        if (viewport.widthPx <= 0f || viewport.heightPx <= 0f || viewport.chartWidthPx <= 0f) {
            return LintChartRenderModel()
        }

        val chartCount = (viewport.widthPx / viewport.chartWidthPx).toInt()
        if (chartCount <= 0) {
            return LintChartRenderModel()
        }

        val visibleHistory = if (history.size <= chartCount) {
            history
        } else {
            history.takeLast(chartCount)
        }
        if (visibleHistory.isEmpty()) {
            return LintChartRenderModel()
        }

        val startX = viewport.widthPx - visibleHistory.size * viewport.chartWidthPx
        val barWidth = viewport.chartWidthPx * 0.9f
        val bars = ArrayList<LintChartBarModel>(visibleHistory.size)

        visibleHistory.forEachIndexed { index, ratio ->
            val level = when {
                ratio > 85f -> LintChartBarModel.Level.HIGH
                ratio > 65f -> LintChartBarModel.Level.MID
                else -> LintChartBarModel.Level.DEFAULT
            }
            val alpha = if (ratio > 50f) {
                1f
            } else {
                (0.5f + (ratio / 100f)).bounds(0f, 1f)
            }
            val top = ((100f - ratio) * viewport.heightPx / 100f)
                .coerceIn(0f, viewport.heightPx - viewport.minBarHeightPx)

            bars += LintChartBarModel(
                ratio = ratio,
                x = viewport.chartWidthPx * index + startX,
                top = top,
                width = barWidth,
                alpha = alpha,
                level = level,
            )
        }

        return LintChartRenderModel(bars = bars)
    }
}

@Composable
fun rememberChartState(
    initialStates: List<ChartState> = List(100) { ChartState(0f) },
): LintChartState {
    val state = rememberSaveable(saver = lintChartStateSaver) {
        LintChartState(initialStates.map { (it.progress * 100f).bounds(0f, 100f) })
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state, scope) {
        state.bindScope(scope)
    }
    return state
}

internal fun Float.bounds(min: Float, max: Float) = if (this >= max) max else if (this <= min) min else this

@Composable
fun LintStackChart(
    modifier: Modifier = Modifier,
    state: LintChartState,
    chartWith: Dp = 10.dp,
    defaultColor: Color = MiuixTheme.colorScheme.primary.copy(alpha = .125f),
    midColor: Color = Color(0xFFFC8A1B),
    highColor: Color = Color(0xFFF9592F),
) {
    val density = LocalDensity.current
    val chartWidthPx = remember(chartWith, density) {
        with(density) { chartWith.toPx() }
    }
    val minBarHeightPx = remember(density) {
        with(density) { 2.dp.toPx() }
    }
    val renderModel = state.renderModel

    Canvas(
        modifier = modifier.onSizeChanged { size ->
            state.updateViewport(
                widthPx = size.width.toFloat(),
                heightPx = size.height.toFloat(),
                chartWidthPx = chartWidthPx,
                minBarHeightPx = minBarHeightPx,
            )
        }
    ) {
        renderModel.bars.forEach { bar ->
            val baseColor = when (bar.level) {
                LintChartBarModel.Level.HIGH -> highColor
                LintChartBarModel.Level.MID -> midColor
                LintChartBarModel.Level.DEFAULT -> defaultColor
            }
            val chartColor = if (bar.level == LintChartBarModel.Level.DEFAULT && bar.ratio <= 50f) {
                baseColor.copy(alpha = bar.alpha)
            } else if (bar.ratio > 50f) {
                baseColor.copy(alpha = 1f)
            } else {
                baseColor.copy(alpha = bar.alpha)
            }
            drawRoundRect(
                color = chartColor,
                topLeft = Offset(x = bar.x, y = bar.top),
                size = Size(bar.width, size.height - bar.top),
                cornerRadius = CornerRadius(2.5f, 2.5f)
            )
        }
    }
}
