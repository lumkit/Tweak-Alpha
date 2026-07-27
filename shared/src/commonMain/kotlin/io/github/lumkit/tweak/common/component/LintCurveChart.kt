package io.github.lumkit.tweak.common.component

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val curveJson = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val lintCurveChartStateSaver = object : Saver<LintCurveChartState, String> {
    override fun restore(value: String): LintCurveChartState {
        return LintCurveChartState(curveJson.decodeFromString(value))
    }

    override fun SaverScope.save(value: LintCurveChartState): String {
        return curveJson.encodeToString(value.snapshot())
    }
}

@Immutable
internal data class LintCurveViewport(
    val widthPx: Float = 0f,
    val heightPx: Float = 0f,
    val stepPx: Float = 0f,
    val endPaddingPx: Float = 0f,
    /** 上下内边距，避免 0/1 极值时线宽与末端光晕被裁切 */
    val verticalInsetPx: Float = 0f,
    /** 可视区左侧额外绘制的历史点数，避免左移动画时边缘断层 */
    val overscanCount: Int = 4,
)

@Immutable
internal data class LintCurvePointModel(
    val x: Float,
    val y: Float,
)

@Immutable
internal data class LintCurveRenderModel(
    val points: List<LintCurvePointModel> = emptyList(),
    val stepPx: Float = 0f,
    /** 每次 [LintCurveChartState.push] / [LintCurveChartState.clear] 递增，用于驱动左移动画 */
    val revision: Int = 0,
    /** 本次渲染是否应播放左移（仅数据 push，视口变化为 false） */
    val shouldAnimate: Boolean = false,
)

/**
 * 平滑曲线图状态：与 [LintChartState] 相同的 push / clear 语义（progress ∈ [0,1]）。
 */
@Stable
class LintCurveChartState internal constructor(initialHistory: List<Float> = emptyList()) {
    private val historyValues = initialHistory.toMutableList()
    private val lock = Any()
    private var viewport = LintCurveViewport()
    private var scope: CoroutineScope? = null
    private var computeJob: Job? = null
    private var dataRevision: Int = 0
    @Volatile
    private var animateOnNextRender: Boolean = false

    internal var renderModel by mutableStateOf(LintCurveRenderModel())
        private set

    /** 仅在 push 完成渲染后递增，供 UI 播放左移动画 */
    internal var pushGeneration by mutableStateOf(0)
        private set

    internal fun snapshot(): List<Float> = synchronized(lock) { historyValues.toList() }

    internal fun bindScope(scope: CoroutineScope) {
        this.scope = scope
        scheduleRecompute()
    }

    internal fun updateViewport(
        widthPx: Float,
        heightPx: Float,
        stepPx: Float,
        endPaddingPx: Float,
        verticalInsetPx: Float,
        overscanCount: Int,
    ) {
        val next = LintCurveViewport(
            widthPx = widthPx,
            heightPx = heightPx,
            stepPx = stepPx,
            endPaddingPx = endPaddingPx,
            verticalInsetPx = verticalInsetPx.coerceAtLeast(0f),
            overscanCount = overscanCount.coerceAtLeast(0),
        )
        if (viewport == next) return
        viewport = next
        synchronized(lock) { trimHistoryLocked() }
        scheduleRecompute()
    }

    fun push(state: ChartState) = push(state.progress)

    fun push(@FloatRange(from = 0.0, to = 1.0) progress: Float) {
        synchronized(lock) {
            historyValues.add((progress * 100f).bounds(0f, 100f))
            trimHistoryLocked()
            dataRevision += 1
        }
        animateOnNextRender = true
        scheduleRecompute()
    }

    fun clear() {
        synchronized(lock) {
            historyValues.clear()
            dataRevision += 1
        }
        animateOnNextRender = false
        scheduleRecompute()
    }

    /** 只保留绘制窗口所需点数，避免长时间 push 导致历史无界增长 */
    private fun trimHistoryLocked() {
        val keep = historyKeepCountLocked()
        val overflow = historyValues.size - keep
        if (overflow > 0) {
            historyValues.subList(0, overflow).clear()
        }
    }

    private fun historyKeepCountLocked(): Int {
        val vp = viewport
        if (vp.widthPx <= 0f || vp.stepPx <= 0f) {
            return DEFAULT_HISTORY_KEEP
        }
        val usableWidth = (vp.widthPx - vp.endPaddingPx).coerceAtLeast(0f)
        val visibleCapacity = ((usableWidth / vp.stepPx).toInt() + 1).coerceAtLeast(1)
        return (visibleCapacity + vp.overscanCount + HISTORY_KEEP_PADDING)
            .coerceIn(MIN_HISTORY_KEEP, MAX_HISTORY_KEEP)
    }

    private fun scheduleRecompute() {
        val currentScope = scope ?: return
        val currentViewport = viewport
        computeJob?.cancel()
        computeJob = currentScope.launch {
            val (historySnapshot, revision) = synchronized(lock) {
                historyValues.toList() to dataRevision
            }
            // 先读勿清：任务若被取消，下一次 recompute 仍能播动画
            val wantAnimate = animateOnNextRender
            val model = withContext(Dispatchers.Default) {
                buildRenderModel(historySnapshot, currentViewport, revision, wantAnimate)
            }
            this@LintCurveChartState.renderModel = model
            if (wantAnimate && model.points.isNotEmpty() && model.stepPx > 0f) {
                animateOnNextRender = false
                pushGeneration += 1
            }
        }
    }

    private fun buildRenderModel(
        history: List<Float>,
        viewport: LintCurveViewport,
        revision: Int,
        shouldAnimate: Boolean,
    ): LintCurveRenderModel {
        if (viewport.widthPx <= 0f || viewport.heightPx <= 0f || viewport.stepPx <= 0f) {
            return LintCurveRenderModel(revision = revision)
        }

        val usableWidth = (viewport.widthPx - viewport.endPaddingPx).coerceAtLeast(0f)
        val visibleCapacity = ((usableWidth / viewport.stepPx).toInt() + 1).coerceAtLeast(1)
        // 左侧多取 overscan 个历史点（x 可 < 0），左移时边缘有曲线过渡
        val totalCapacity = visibleCapacity + viewport.overscanCount
        val samples = if (history.size <= totalCapacity) history else history.takeLast(totalCapacity)
        if (samples.isEmpty()) {
            return LintCurveRenderModel(stepPx = viewport.stepPx, revision = revision)
        }

        // 右对齐：最新点贴在 endPadding 左侧；更早的点向左延伸到可视区外
        val rightX = viewport.widthPx - viewport.endPaddingPx
        val startX = rightX - (samples.size - 1) * viewport.stepPx
        // 映射到 [inset, height-inset]，极值点与光晕完整可见
        val inset = viewport.verticalInsetPx.coerceIn(0f, viewport.heightPx / 2f)
        val topY = inset
        val usableHeight = (viewport.heightPx - inset * 2f).coerceAtLeast(0f)
        val points = ArrayList<LintCurvePointModel>(samples.size)
        samples.forEachIndexed { index, ratio ->
            val y = topY + ((100f - ratio) / 100f * usableHeight)
            points += LintCurvePointModel(
                x = startX + index * viewport.stepPx,
                y = y,
            )
        }
        return LintCurveRenderModel(
            points = points,
            stepPx = viewport.stepPx,
            revision = revision,
            shouldAnimate = shouldAnimate,
        )
    }

    private companion object {
        const val MIN_HISTORY_KEEP = 32
        const val DEFAULT_HISTORY_KEEP = 128
        const val MAX_HISTORY_KEEP = 512
        const val HISTORY_KEEP_PADDING = 8
    }
}

@Composable
fun rememberCurveChartState(
    initialStates: List<ChartState> = List(60) { ChartState(0f) },
): LintCurveChartState {
    val state = rememberSaveable(saver = lintCurveChartStateSaver) {
        LintCurveChartState(initialStates.map { (it.progress * 100f).bounds(0f, 100f) })
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state, scope) {
        state.bindScope(scope)
    }
    return state
}

/**
 * 平滑曲线 Sparkline（[LintStackChart] 的曲线版）。
 *
 * - 三次贝塞尔平滑折线
 * - 线下垂直渐变填充
 * - 末端实心点 + 半透明光晕
 * - [LintCurveChartState.push] 新增数据时整体左移动画
 */
@Composable
fun LintCurveChart(
    modifier: Modifier = Modifier,
    state: LintCurveChartState,
    /** 相邻采样点水平间距 */
    step: Dp = 32.dp,
    lineWidth: Dp = 2.dp,
    lineColor: Color = MiuixTheme.colorScheme.primary,
    fillColor: Color = MiuixTheme.colorScheme.primaryContainer,
    endDotRadius: Dp = 3.dp,
    endHaloRadius: Dp = 6.dp,
    endHaloColor: Color = MiuixTheme.colorScheme.primaryContainer.copy(.5f),
    animationDurationMs: Int = 320,
    /** 可视区左侧额外历史点数，保证左移动画边缘平滑 */
    overscanCount: Int = 6,
) {
    val density = LocalDensity.current
    val stepPx = remember(step, density) { with(density) { step.toPx() } }
    val lineWidthPx = remember(lineWidth, density) { with(density) { lineWidth.toPx() } }
    val endDotRadiusPx = remember(endDotRadius, density) { with(density) { endDotRadius.toPx() } }
    val endHaloRadiusPx = remember(endHaloRadius, density) { with(density) { endHaloRadius.toPx() } }
    val endPaddingPx = remember(endHaloRadiusPx) { endHaloRadiusPx + 2f }
    // 上下留出光晕/线宽空间，progress=0/1 时不被裁切
    val verticalInsetPx = remember(endHaloRadiusPx, lineWidthPx) {
        maxOf(endHaloRadiusPx + 2f, lineWidthPx / 2f + 1f)
    }

    val renderModel = state.renderModel
    val shiftAnim = remember { Animatable(0f) }
    val endYAnim = remember { Animatable(Float.NaN) }
    val pushGeneration = state.pushGeneration
    val scope = rememberCoroutineScope()
    // 上一帧已接入动画的 generation；新 model 在 snap 完成前必须直接以 shift=1 绘制，
    // 否则会先闪一帧最终位置（shift=0）。
    var appliedGeneration by remember { mutableIntStateOf(0) }
    var launchedGeneration by remember { mutableIntStateOf(0) }
    var shiftJob by remember { mutableStateOf<Job?>(null) }
    val needsShiftStart =
        pushGeneration > appliedGeneration &&
            pushGeneration > 0 &&
            renderModel.stepPx > 0f &&
            renderModel.shouldAnimate

    // snap 完成前强制 shift=1，与旧曲线在 x 上对齐；完成后由 Animatable 驱动
    val shiftFraction = if (needsShiftStart) 1f else shiftAnim.value

    val targetEndY = renderModel.points.lastOrNull()?.y
    // 末端点 X 固定，Y 做位移动画（与曲线左移同时长）
    LaunchedEffect(targetEndY, pushGeneration) {
        if (targetEndY == null) {
            endYAnim.snapTo(Float.NaN)
            return@LaunchedEffect
        }
        if (endYAnim.value.isNaN()) {
            endYAnim.snapTo(targetEndY)
            return@LaunchedEffect
        }
        endYAnim.animateTo(
            targetValue = targetEndY,
            animationSpec = tween(
                durationMillis = animationDurationMs,
                easing = LinearEasing,
            ),
        )
    }

    SideEffect {
        if (!needsShiftStart || launchedGeneration == pushGeneration) return@SideEffect
        val gen = pushGeneration
        launchedGeneration = gen
        shiftJob?.cancel()
        shiftJob = scope.launch {
            shiftAnim.snapTo(1f)
            appliedGeneration = gen
            shiftAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = animationDurationMs,
                    easing = LinearEasing,
                ),
            )
        }
    }

    val animatedEndY = endYAnim.value.let { if (it.isNaN()) targetEndY else it }

    Canvas(
        modifier = modifier.onSizeChanged { size ->
            state.updateViewport(
                widthPx = size.width.toFloat(),
                heightPx = size.height.toFloat(),
                stepPx = stepPx,
                endPaddingPx = endPaddingPx,
                verticalInsetPx = verticalInsetPx,
                overscanCount = overscanCount,
            )
        },
    ) {
        // 裁剪到组件范围：区外 overscan 只参与路径，不画出框外
        clipRect {
            val points = renderModel.points
            if (points.isEmpty() || animatedEndY == null) return@clipRect

            val shiftX = shiftFraction * renderModel.stepPx
            // 历史点左移；末端点 X 固定，Y 用动画值，曲线与圆点始终落在同一锚点
            val lastIndex = points.lastIndex
            val endAnchor = Offset(points[lastIndex].x, animatedEndY)
            val offsets = points.mapIndexed { index, point ->
                if (index == lastIndex) {
                    endAnchor
                } else {
                    Offset(point.x + shiftX, point.y)
                }
            }
            if (offsets.size == 1) {
                drawEndMarker(
                    center = endAnchor,
                    haloRadius = endHaloRadiusPx,
                    haloColor = endHaloColor,
                    dotRadius = endDotRadiusPx,
                    dotColor = lineColor,
                )
                return@clipRect
            }

            val linePath = buildLintSmoothPath(offsets)
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(endAnchor.x, size.height)
                lineTo(offsets.first().x, size.height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor.copy(.15f), fillColor.copy(alpha = 0f)),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = lineWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawEndMarker(
                center = endAnchor,
                haloRadius = endHaloRadiusPx,
                haloColor = endHaloColor,
                dotRadius = endDotRadiusPx,
                dotColor = lineColor,
            )
        }
    }
}

private fun DrawScope.drawEndMarker(
    center: Offset,
    haloRadius: Float,
    haloColor: Color,
    dotRadius: Float,
    dotColor: Color,
) {
    drawCircle(color = haloColor, radius = haloRadius, center = center)
    drawCircle(color = dotColor, radius = dotRadius, center = center)
}

/** 水平中点控制的三次贝塞尔，峰谷更圆润 */
private fun buildLintSmoothPath(points: List<Offset>): Path {
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
