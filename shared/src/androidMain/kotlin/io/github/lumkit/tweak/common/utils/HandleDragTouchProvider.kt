package io.github.lumkit.tweak.common.utils

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import io.github.lumkit.tweak.application
import kotlin.math.abs

class HandleDragTouchProvider(
    private val context: Context = application,
    private val handleHeightPx: Int,
    private val touchSlop: Int = 10,
    private val restrictedArea: Boolean = true,
) : OverlayTouchProvider {

    var onPositionSettled: ((x: Int, y: Int) -> Unit)? = null
    var onInteractionStart: (() -> Unit)? = null
    var onInteractionEnd: (() -> Unit)? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isTouching = false
    private var handlePressed = false

    override fun onInterceptTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handlePressed = event.y <= handleHeightPx
                if (!handlePressed) {
                    return false
                }
                notifyInteractionStart()
                isDragging = false
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!handlePressed) {
                    return false
                }
                if (!isDragging) {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        return true
                    }
                }
                return isDragging
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    return true
                }
                val wasTouchingHandle = handlePressed
                handlePressed = false
                if (wasTouchingHandle) {
                    notifyInteractionEnd()
                }
                return false
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
                val wasDragging = isDragging
                val wasTouchingHandle = handlePressed || isTouching
                handlePressed = false
                isDragging = false
                if (wasTouchingHandle) {
                    notifyInteractionEnd()
                }
                if (wasDragging) {
                    onPositionSettled?.invoke(params.x, params.y)
                }
                return wasDragging
            }
        }
        return false
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
