package io.github.lumkit.tweak.common.utils

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import io.github.lumkit.tweak.application
import kotlin.math.abs

/**
 * 自由拖动触摸提供者
 *
 * 支持拖拽移动、单击、双击，不做吸边处理。
 */
class DragTouchProvider(
    private val context: Context = application,
    private val touchSlop: Int = 10,
) : OverlayTouchProvider {

    var onPositionSettled: ((x: Int, y: Int) -> Unit)? = null
    var onInteractionStart: (() -> Unit)? = null
    var onInteractionEnd: (() -> Unit)? = null
    var onClick: (() -> Unit)? = null
    var onDoubleClick: (() -> Unit)? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isTouching = false
    private var consumeTapUpEvent = false
    private var lastTapUpTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val pendingSingleTap = Runnable {
        onClick?.invoke()
    }

    override fun onInterceptTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                return true
            }
        }
        return true
    }

    override fun onTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean {
        val screen = getRealScreenSize()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumeTapUpEvent = false
                notifyInteractionStart()
                isDragging = false
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        mainHandler.removeCallbacks(pendingSingleTap)
                        isDragging = true
                    }
                }
                val newX = initialX + (event.rawX - initialTouchX).toInt()
                val newY = initialY + (event.rawY - initialTouchY).toInt()
                val viewWidth = view.measuredWidth.takeIf { it > 0 } ?: view.width
                val viewHeight = view.measuredHeight.takeIf { it > 0 } ?: view.height
                params.x = newX.coerceIn(0, (screen.x - viewWidth).coerceAtLeast(0))
                params.y = newY.coerceIn(0, (screen.y - viewHeight).coerceAtLeast(0))
                runCatching { windowManager.updateViewLayout(view, params) }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && !isDragging) {
                    handleTapEvent()
                }
                consumeTapUpEvent = false
                isDragging = false
                notifyInteractionEnd()
                onPositionSettled?.invoke(params.x, params.y)
                return true
            }
        }
        return false
    }

    private fun handleTapEvent() {
        val now = System.currentTimeMillis()
        if (now - lastTapUpTime <= doubleTapTimeout) {
            mainHandler.removeCallbacks(pendingSingleTap)
            lastTapUpTime = 0L
            onDoubleClick?.invoke()
            return
        }
        lastTapUpTime = now
        mainHandler.removeCallbacks(pendingSingleTap)
        mainHandler.postDelayed(pendingSingleTap, doubleTapTimeout)
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
