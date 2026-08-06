package io.github.lumkit.tweak.common.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * 拖动范围避开状态栏 / 导航栏 / 挖孔（含沉浸式预留）。
 */
class DragTouchProvider(
    private val context: Context = application,
    private val touchSlop: Int = 10,
    private val restrictedArea: Boolean = true,
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
                val (clampedX, clampedY) = clampPosition(
                    windowManager = windowManager,
                    context = context,
                    x = newX,
                    y = newY,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                )
                params.x = clampedX
                params.y = clampedY
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

    override fun clampPosition(
        windowManager: WindowManager,
        context: Context,
        x: Int,
        y: Int,
        viewWidth: Int,
        viewHeight: Int,
        edgePadding: Int,
    ): Pair<Int, Int> {
        return if (restrictedArea) {
            super.clampPosition(
                windowManager = windowManager,
                context = context,
                x = x,
                y = y,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                edgePadding = edgePadding,
            )
        } else {
            OverlayScreenBounds.clampFullScreen(
                windowManager = windowManager,
                x = x,
                y = y,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                edgePadding = edgePadding,
            )
        }
    }
}
