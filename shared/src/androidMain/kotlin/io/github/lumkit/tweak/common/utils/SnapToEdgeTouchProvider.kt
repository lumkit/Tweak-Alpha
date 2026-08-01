package io.github.lumkit.tweak.common.utils

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import io.github.lumkit.tweak.application
import kotlin.math.abs

/**
 * 吸边触摸提供者
 *
 * 支持拖拽移动 + 松手后自动吸附到左右安全边缘（避开系统栏 / 挖孔），
 * 短距离触摸视为点击，事件透传给子组件；超过阈值后拦截为拖拽。
 *
 * @param animationDuration 吸边动画时长（毫秒）
 * @param edgePadding 吸边后距离安全区边缘的间距（像素）
 * @param touchSlop 拖拽判定阈值（像素），超过此距离才认为是拖拽
 */
class SnapToEdgeTouchProvider(
    private val context: Context = application,
    private val animationDuration: Long = 250L,
    private val edgePadding: Int = 0,
    private val touchSlop: Int = 10,
) : OverlayTouchProvider {

    /** 松手吸边结束后回调，参数为最终位置 (x, y) */
    var onPositionSettled: ((x: Int, y: Int) -> Unit)? = null
    /** 用户开始与悬浮窗交互时回调 */
    var onInteractionStart: (() -> Unit)? = null
    /** 用户结束与悬浮窗交互时回调 */
    var onInteractionEnd: (() -> Unit)? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isTouching = false
    private var animator: ValueAnimator? = null

    override fun onInterceptTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                notifyInteractionStart()
                animator?.cancel()
                isDragging = false
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                notifyInteractionEnd()
            }
        }
        return isDragging
    }

    override fun onTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val newX = initialX + (event.rawX - initialTouchX).toInt()
                val newY = initialY + (event.rawY - initialTouchY).toInt()

                val viewWidth = view.measuredWidth.takeIf { it > 0 } ?: view.width
                val viewHeight = view.measuredHeight.takeIf { it > 0 } ?: view.height
                val (clampedX, clampedY) = OverlayScreenBounds.clamp(
                    windowManager = windowManager,
                    context = context,
                    x = newX,
                    y = newY,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    edgePadding = edgePadding,
                )
                params.x = clampedX
                params.y = clampedY
                runCatching { windowManager.updateViewLayout(view, params) }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                notifyInteractionEnd()
                snapToEdge(view, params, windowManager)
                return true
            }
        }
        return false
    }

    /**
     * 主动触发吸边（用于内容大小变化后重新吸附）
     */
    fun requestReSnap(
        view: View,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
    ) {
        animator?.cancel()
        view.post {
            snapToEdge(view, params, windowManager)
        }
    }

    private fun snapToEdge(
        view: View,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
    ) {
        val viewWidth = view.measuredWidth.takeIf { it > 0 } ?: view.width
        val viewHeight = view.measuredHeight.takeIf { it > 0 } ?: view.height
        val bounds = OverlayScreenBounds.of(
            windowManager = windowManager,
            context = context,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            edgePadding = edgePadding,
        )
        val centerX = params.x + viewWidth / 2
        val targetX = if (centerX < bounds.screenWidth / 2) {
            bounds.minX
        } else {
            bounds.maxX
        }
        val targetY = params.y.coerceIn(bounds.minY, bounds.maxY)

        val startX = params.x
        val startY = params.y
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                runCatching { windowManager.updateViewLayout(view, params) }
                if (fraction == 1f) {
                    onPositionSettled?.invoke(params.x, params.y)
                }
            }
            start()
        }
    }

    private fun notifyInteractionStart() {
        if (isTouching) {
            return
        }
        isTouching = true
        onInteractionStart?.invoke()
    }

    private fun notifyInteractionEnd() {
        if (!isTouching) {
            return
        }
        isTouching = false
        onInteractionEnd?.invoke()
    }
}
