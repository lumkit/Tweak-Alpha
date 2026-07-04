package io.github.lumkit.tweak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 无障碍服务，用于监听前台应用切换
 */
@SuppressLint("AccessibilityPolicy")
class TweakAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "TweakAccessibilityService"
        internal val _foregroundPackage = MutableStateFlow<String?>(null)
        internal val _isRunning = MutableStateFlow(false)

    }

    private var keepAliveView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = null // 监听所有应用，不做包名过滤
            notificationTimeout = 100
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
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
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: return

            // 过滤非 Activity 窗口（系统弹窗、悬浮窗等）
            if (!isActivity(pkg, className)) return

            if (pkg != _foregroundPackage.value) {
                _foregroundPackage.value = pkg
                logD("foreground: $pkg ($className)", TAG)
            }
        }
    }

    /**
     * 通过 PackageManager 验证该类名是否为目标包中已注册的 Activity
     */
    private fun isActivity(packageName: String, className: String): Boolean {
        return try {
            val componentName = ComponentName(packageName, className)
            packageManager.getActivityInfo(componentName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {
        logD("onInterrupt", TAG)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeKeepAliveOverlay()
        _isRunning.value = false
        _foregroundPackage.value = null
        logD("onDestroy", TAG)
    }
}
