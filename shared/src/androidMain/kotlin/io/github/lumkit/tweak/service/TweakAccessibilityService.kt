package io.github.lumkit.tweak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import io.github.lumkit.tweak.common.shell.ReusableShells
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
    }

    private var keepAliveView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolveForegroundJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        ForegroundAppMonitor._isRunning.value = true

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
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: return

            // 过滤非 Activity 窗口（系统弹窗、悬浮窗等）
            if (!isActivity(pkg, className)) return

            resolveForegroundJob?.cancel()
            resolveForegroundJob = serviceScope.launch {
                val actualForegroundPackage = resolveForegroundPackageName() ?: pkg
                if (actualForegroundPackage != ForegroundAppMonitor._foregroundPackage.value) {
                    ForegroundAppMonitor._foregroundPackage.value = actualForegroundPackage
                    logD(
                        "foreground: $actualForegroundPackage (event=$pkg, class=$className)",
                        TAG
                    )
                }
            }
        }
    }

    private suspend fun resolveForegroundPackageName(): String? {
        val candidates = listOf(
            "dumpsys activity activities",
            "dumpsys window windows"
        )
        candidates.forEach { cmd ->
            runCatching {
                ReusableShells.execSync(cmd)
                    .extractForegroundPackageName()
            }.getOrNull()?.let { packageName ->
                if (packageName.isNotBlank()) {
                    return packageName
                }
            }
        }
        return null
    }

    private fun String.extractForegroundPackageName(): String? {
        val interestingLines = lineSequence().filter {
            it.contains("mResumedActivity") ||
                    it.contains("topResumedActivity") ||
                    it.contains("mCurrentFocus") ||
                    it.contains("mFocusedApp")
        }
        val patterns = listOf(
            Regex("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/[a-zA-Z0-9_.$]+")
        )
        interestingLines.forEach { line ->
            patterns.forEach { pattern ->
                pattern.find(line)?.groupValues?.getOrNull(1)?.let { return it }
            }
        }
        return null
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
        resolveForegroundJob?.cancel()
        removeKeepAliveOverlay()
        ForegroundAppMonitor._isRunning.value = false
        ForegroundAppMonitor._foregroundPackage.value = null
        logD("onDestroy", TAG)
    }
}
