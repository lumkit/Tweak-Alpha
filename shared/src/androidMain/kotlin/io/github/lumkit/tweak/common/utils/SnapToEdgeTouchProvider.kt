package io.github.lumkit.tweak.common.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import io.github.lumkit.tweak.application
import kotlin.math.abs

/**
 * 吸边触摸提供者
 *
 * 支持拖拽移动 + 松手后自动吸附到左右边缘，且不超出物理屏幕边界。
 * 悬浮窗可以显示在状态栏和导航栏上方（全屏覆盖）。
 * 短距离触摸视为点击，事件透传给子组件；超过阈值后拦截为拖拽。
 *
 * @param animationDuration 吸边动画时长（毫秒）
 * @param edgePadding 吸边后距离屏幕边缘的间距（像素）
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

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var animator: ValueAnimator? = null

    override fun onInterceptTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
        val screen = getRealScreenSize()

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val newX = initialX + (event.rawX - initialTouchX).toInt()
                val newY = initialY + (event.rawY - initialTouchY).toInt()

                val viewWidth = view.measuredWidth.takeIf { it > 0 } ?: view.width
                val viewHeight = view.measuredHeight.takeIf { it > 0 } ?: view.height

                // 限制不超出物理屏幕
                params.x = newX.coerceIn(0, (screen.x - viewWidth).coerceAtLeast(0))
                params.y = newY.coerceIn(0, (screen.y - viewHeight).coerceAtLeast(0))
                runCatching { windowManager.updateViewLayout(view, params) }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                snapToEdge(view, params, windowManager, screen)
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
            snapToEdge(view, params, windowManager, getRealScreenSize())
        }
    }

    private fun snapToEdge(
        view: View,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        screen: Point,
    ) {
        val viewWidth = view.measuredWidth.takeIf { it > 0 } ?: view.width
        val viewHeight = view.measuredHeight.takeIf { it > 0 } ?: view.height
        val centerX = params.x + viewWidth / 2

        // 目标 X：吸附到左边或右边
        val targetX = if (centerX < screen.x / 2) {
            edgePadding
        } else {
            (screen.x - viewWidth - edgePadding).coerceAtLeast(0)
        }

        // 限制 Y 不超出物理屏幕
        val maxY = (screen.y - viewHeight).coerceAtLeast(0)
        val targetY = params.y.coerceIn(0, maxY)

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

    /**
     * 获取物理屏幕尺寸（包含状态栏和导航栏）
     */
    private fun getRealScreenSize(): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Point(metrics.widthPixels, metrics.heightPixels)
        }
    }
}
