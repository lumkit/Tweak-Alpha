package io.github.lumkit.tweak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.LruCache
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import io.github.lumkit.tweak.common.utils.ForegroundAppMonitor
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 无障碍服务，用于监听前台应用切换
 */
@SuppressLint("AccessibilityPolicy")
class TweakAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TweakAccessibilityService"
        private val IGNORED_WINDOW_TYPES = setOf(
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
            AccessibilityWindowInfo.TYPE_INPUT_METHOD,
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER,
            AccessibilityWindowInfo.TYPE_SYSTEM,
        )
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
        )
    }

    private var keepAliveView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolveForegroundJob: Job? = null
    private val windowPackageCache = LruCache<Int, String>(16)

    override fun onServiceConnected() {
        super.onServiceConnected()
        ForegroundAppMonitor._isRunning.value = true

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = null // 监听所有应用，不做包名过滤
            notificationTimeout = 0
        }

        addKeepAliveOverlay()
        logD("TweakAccessibilityService is connected.", TAG)
    }

    /**
     * 添加 1x1 透明悬浮窗，防止 MIUI/HyperOS 冻结进程导致事件回调暂停
     */
    private fun addKeepAliveOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = View(this).apply {
            // 完全透明且不可交互
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(view, params)
            keepAliveView = view
            logD("Keep-alive overlay added.", TAG)
        } catch (e: Exception) {
            logD("Failed to add keep-alive overlay: ${e.message}", TAG)
        }
    }

    private fun removeKeepAliveOverlay() {
        keepAliveView?.let {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
            } catch (_: Exception) {}
            keepAliveView = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        resolveForegroundJob?.cancel()
        resolveForegroundJob = serviceScope.launch {
            val actualForegroundPackage = resolveForegroundPackageName(event) ?: return@launch
            if (actualForegroundPackage != ForegroundAppMonitor._foregroundPackage.value) {
                ForegroundAppMonitor._foregroundPackage.value = actualForegroundPackage
                logD(
                    "foreground: $actualForegroundPackage (eventPkg=${event.packageName}, eventClass=${event.className}, eventType=${event.eventType})",
                    TAG
                )
            }
        }
    }

    private fun resolveForegroundPackageName(event: AccessibilityEvent): String? {
        val effectiveWindows = getEffectiveWindows()
        if (effectiveWindows.isEmpty()) {
            return sanitizePackageName(event.packageName?.toString())
        }

        val foregroundWindow = selectForegroundWindow(effectiveWindows)
        val eventPackage = sanitizePackageName(event.packageName?.toString())
        if (foregroundWindow == null) {
            return eventPackage
        }

        if (event.windowId == foregroundWindow.id && eventPackage != null) {
            windowPackageCache.put(foregroundWindow.id, eventPackage)
            return eventPackage
        }

        return getWindowPackageName(foregroundWindow) ?: eventPackage
    }

    private fun getEffectiveWindows(): List<AccessibilityWindowInfo> {
        return windows
            ?.filterNot { window -> window.type in IGNORED_WINDOW_TYPES }
            .orEmpty()
    }

    private fun selectForegroundWindow(
        effectiveWindows: List<AccessibilityWindowInfo>
    ): AccessibilityWindowInfo? {
        if (effectiveWindows.none { it.isActive || it.isFocused }) {
            return null
        }

        var bestWindow: AccessibilityWindowInfo? = null
        var bestWindowArea = -1
        var bestWindowFocused = false
        var bestWindowLayer = Int.MIN_VALUE

        effectiveWindows.forEach { window ->
            val isFocusedWindow = window.isActive || window.isFocused
            if (bestWindowFocused && !isFocusedWindow) {
                return@forEach
            }

            val bounds = Rect()
            runCatching { window.getBoundsInScreen(bounds) }
                .onFailure { return@forEach }
            val area = (bounds.width().coerceAtLeast(0)) * (bounds.height().coerceAtLeast(0))
            if (area <= 0) return@forEach

            val shouldReplace = when {
                bestWindow == null -> true
                isFocusedWindow && !bestWindowFocused -> true
                area > bestWindowArea -> true
                area == bestWindowArea && window.layer > bestWindowLayer -> true
                else -> false
            }

            if (shouldReplace) {
                bestWindow = window
                bestWindowArea = area
                bestWindowFocused = isFocusedWindow
                bestWindowLayer = window.layer
            }
        }

        return bestWindow
    }

    private fun getWindowPackageName(window: AccessibilityWindowInfo): String? {
        windowPackageCache.get(window.id)?.let { cachedPackage ->
            return sanitizePackageName(cachedPackage)
        }

        val packageName = runCatching {
            window.root?.packageName?.toString()
        }.getOrNull()

        return sanitizePackageName(packageName)?.also {
            windowPackageCache.put(window.id, it)
        }
    }

    private fun sanitizePackageName(packageName: String?): String? {
        val normalized = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalized in IGNORED_PACKAGES) {
            return null
        }
        return normalized
    }

    override fun onInterrupt() {
        logD("onInterrupt", TAG)
    }

    override fun onDestroy() {
        super.onDestroy()
        resolveForegroundJob?.cancel()
        windowPackageCache.evictAll()
        removeKeepAliveOverlay()
        ForegroundAppMonitor._isRunning.value = false
        ForegroundAppMonitor._foregroundPackage.value = null
        logD("onDestroy", TAG)
    }
}
