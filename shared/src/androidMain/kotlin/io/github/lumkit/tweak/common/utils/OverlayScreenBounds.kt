package io.github.lumkit.tweak.common.utils

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.max

/**
 * 悬浮窗可拖动/可放置的安全矩形。
 * 系统栏可见时避开 status/nav bar；沉浸式栏隐藏后可进入原 bar 区域。
 * 挖孔区域始终避开。
 *
 * 坐标原点与 [WindowManager.LayoutParams] 在 `Gravity.START|TOP` + `FLAG_LAYOUT_IN_SCREEN` 下一致：整屏左上角。
 */
data class OverlayDragBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val insetLeft: Int,
    val insetTop: Int,
    val insetRight: Int,
    val insetBottom: Int,
) {
    fun clamp(x: Int, y: Int): Pair<Int, Int> =
        x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
}

object OverlayScreenBounds {

    fun screenSize(windowManager: WindowManager): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealSize(it)
            }
        }
    }

    fun of(
        windowManager: WindowManager,
        context: Context,
        viewWidth: Int,
        viewHeight: Int,
        edgePadding: Int = 0,
    ): OverlayDragBounds {
        val screen = screenSize(windowManager)
        val insets = systemBarInsets(windowManager, context)
        val minX = insets.left + edgePadding
        val minY = insets.top + edgePadding
        val maxX = (screen.x - insets.right - viewWidth - edgePadding).coerceAtLeast(minX)
        val maxY = (screen.y - insets.bottom - viewHeight - edgePadding).coerceAtLeast(minY)
        return OverlayDragBounds(
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            screenWidth = screen.x,
            screenHeight = screen.y,
            insetLeft = insets.left,
            insetTop = insets.top,
            insetRight = insets.right,
            insetBottom = insets.bottom,
        )
    }

    fun clamp(
        windowManager: WindowManager,
        context: Context,
        x: Int,
        y: Int,
        viewWidth: Int,
        viewHeight: Int,
        edgePadding: Int = 0,
    ): Pair<Int, Int> {
        val vw = viewWidth.coerceAtLeast(0)
        val vh = viewHeight.coerceAtLeast(0)
        return of(windowManager, context, vw, vh, edgePadding).clamp(x, y)
    }

    /**
     * 状态栏 / 导航栏 / 挖孔 inset。
     * - status/nav：用 [WindowInsets.getInsets]（可见性敏感），沉浸式隐藏后为 0，可进入原 bar 区
     * - cutout：用 IgnoringVisibility，始终避开挖孔
     */
    fun systemBarInsets(windowManager: WindowManager, context: Context): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsets = windowManager.currentWindowMetrics.windowInsets
            val bars = windowInsets.getInsets(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
            )
            val cutout = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.displayCutout(),
            )
            return Rect(
                max(bars.left, cutout.left),
                max(bars.top, cutout.top),
                max(bars.right, cutout.right),
                max(bars.bottom, cutout.bottom),
            )
        }
        return legacySystemBarInsets(windowManager, context)
    }

    @Suppress("DEPRECATION")
    private fun legacySystemBarInsets(windowManager: WindowManager, context: Context): Rect {
        val res = context.resources
        val status = dimenPx(res, "status_bar_height")
        val nav = dimenPx(res, "navigation_bar_height")
        var left = 0
        var top = status
        var right = 0
        var bottom = 0

        val rotation = runCatching { windowManager.defaultDisplay.rotation }.getOrDefault(Surface.ROTATION_0)
        when (rotation) {
            Surface.ROTATION_90 -> right = nav
            Surface.ROTATION_270 -> left = nav
            Surface.ROTATION_180 -> {
                // 倒竖屏：状态栏多在底部一侧，保守两侧都预留
                top = max(top, 0)
                bottom = max(nav, status)
            }
            else -> bottom = nav
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val cutout = windowManager.defaultDisplay.cutout ?: return@runCatching
                left = max(left, cutout.safeInsetLeft)
                top = max(top, cutout.safeInsetTop)
                right = max(right, cutout.safeInsetRight)
                bottom = max(bottom, cutout.safeInsetBottom)
            }
        }
        return Rect(left, top, right, bottom)
    }

    private fun dimenPx(res: android.content.res.Resources, name: String): Int {
        val id = res.getIdentifier(name, "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }
}
