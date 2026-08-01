package io.github.lumkit.tweak.common.utils

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.lumkit.tweak.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 悬浮窗触摸行为提供者接口
 *
 * 通过实现此接口可自定义悬浮窗的触摸行为（拖拽、吸边、缩放等）。
 * 触摸事件在包装容器层拦截，不影响 Compose 内部子组件的点击等交互。
 */
interface OverlayTouchProvider {
    /**
     * 是否拦截此触摸事件（在 onInterceptTouchEvent 中调用）
     *
     * @return true 表示拦截事件（开始拖拽），false 表示透传给子组件
     */
    fun onInterceptTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean

    /**
     * 处理被拦截的触摸事件（在 onTouchEvent 中调用）
     *
     * @return true 表示已消费事件
     */
    fun onTouchEvent(
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        view: View,
    ): Boolean
}

/**
 * ComposeView 悬浮窗工具类
 *
 * 提供创建、显示、更新、移除 Compose 悬浮窗的能力。
 * 内部自动管理 LifecycleOwner 和 SavedStateRegistryOwner，
 * 支持通过 [OverlayTouchProvider] 自定义触摸行为。
 */
class ComposeOverlayHelper(
    private val context: Context = application,
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var lastScreenFingerprint: String? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (!isShowing) {
                lastScreenFingerprint = currentScreenFingerprint()
                return
            }
            val fingerprint = currentScreenFingerprint()
            if (fingerprint == lastScreenFingerprint) {
                return
            }
            lastScreenFingerprint = fingerprint
            clampToSafeBounds(postIfNeeded = true)
            requestReSnap()
        }
    }
    var touchProvider: OverlayTouchProvider? = null
        private set

    /** 悬浮窗是否正在显示 */
    val isShowing: Boolean get() = rootView != null

    /**
     * 设置触摸行为提供者
     *
     * 在 [show] 之前调用，会替代默认的拖拽行为。
     * 设为 null 时使用默认拖拽（如果 draggable=true）。
     */
    fun setTouchProvider(provider: OverlayTouchProvider?) {
        touchProvider = provider
    }

    /**
     * 显示悬浮窗
     *
     * @param width 宽度，默认 WRAP_CONTENT
     * @param height 高度，默认 WRAP_CONTENT
     * @param gravity 位置，默认左上角
     * @param x 初始 X 偏移
     * @param y 初始 Y 偏移
     * @param draggable 是否可拖拽移动
     * @param content Compose UI 内容
     */
    fun show(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.START or Gravity.TOP,
        x: Int = 0,
        y: Int = 100,
        draggable: Boolean = true,
        content: @Composable ComposeOverlayHelper.() -> Unit,
    ) {
        if (isShowing) return

        val params = createLayoutParams(width, height, gravity, x, y)
        val owner = OverlayLifecycleOwner()

        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // 设置 Recomposer
            val coroutineContext = AndroidUiDispatcher.CurrentThread
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val recomposer = Recomposer(coroutineContext)
            compositionContext = recomposer
            scope.launch { recomposer.runRecomposeAndApplyChanges() }

            setContent {
                content(this@ComposeOverlayHelper)
            }
        }

        // 构建根视图：有 TouchProvider 时用拦截容器包装，否则使用默认拖拽或直接用 ComposeView
        val provider = touchProvider
        val root: View = if (provider != null) {
            OverlayInterceptLayout(context, provider, params, windowManager).apply {
                addView(compose, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
            }
        } else {
            if (draggable) {
                setupDrag(compose, params)
            }
            compose
        }

        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager.addView(root, params)
        rootView = root
        composeView = compose
        lifecycleOwner = owner
        lastScreenFingerprint = currentScreenFingerprint()
        registerDisplayListener()
        root.setOnApplyWindowInsetsListener { _, insets ->
            // 系统栏显隐变化时按可见 inset 重新限制；不强制吸边，避免沉浸切换时跳动
            clampToSafeBounds(postIfNeeded = false)
            insets
        }
        root.requestApplyInsets()
        root.post {
            clampToSafeBounds(postIfNeeded = false)
        }
    }

    /**
     * 更新悬浮窗内容
     */
    fun updateContent(content: @Composable () -> Unit) {
        composeView?.setContent(content)
    }

    /**
     * 移除悬浮窗
     */
    fun dismiss() {
        unregisterDisplayListener()
        rootView?.let { view ->
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager.removeView(view)
        }
        rootView = null
        composeView = null
        lifecycleOwner = null
        lastScreenFingerprint = null
    }

    /**
     * 获取悬浮窗当前位置
     *
     * @return Pair(x, y)，未显示时返回 null
     */
    fun getPosition(): Pair<Int, Int>? {
        val view = rootView ?: return null
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return null
        return params.x to params.y
    }

    /**
     * 更新悬浮窗位置（自动 clamp 到系统栏安全区）
     */
    fun updatePosition(x: Int, y: Int) {
        val view = rootView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        val viewWidth = view.measuredWidth.takeIf { it > 0 } ?: view.width
        val viewHeight = view.measuredHeight.takeIf { it > 0 } ?: view.height
        val (clampedX, clampedY) = if (viewWidth > 0 && viewHeight > 0) {
            OverlayScreenBounds.clamp(
                windowManager = windowManager,
                context = context,
                x = x,
                y = y,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
            )
        } else {
            x to y
        }
        params.x = clampedX
        params.y = clampedY
        windowManager.updateViewLayout(view, params)
    }

    /**
     * 将当前位置限制在状态栏 / 导航栏 / 挖孔之外的安全区。
     * 尺寸尚未 layout 完成时会 [post] 重试。
     */
    fun clampToSafeBounds(postIfNeeded: Boolean = true) {
        val view = rootView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val viewWidth = view.measuredWidth.takeIf { it > 0 } ?: view.width
        val viewHeight = view.measuredHeight.takeIf { it > 0 } ?: view.height
        if (viewWidth <= 0 || viewHeight <= 0) {
            if (postIfNeeded) {
                view.post { clampToSafeBounds(postIfNeeded = false) }
            }
            return
        }
        val (clampedX, clampedY) = OverlayScreenBounds.clamp(
            windowManager = windowManager,
            context = context,
            x = params.x,
            y = params.y,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
        )
        if (clampedX == params.x && clampedY == params.y) {
            return
        }
        params.x = clampedX
        params.y = clampedY
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /**
     * 更新悬浮窗大小
     */
    fun updateSize(width: Int, height: Int) {
        val view = rootView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        params.width = width
        params.height = height
        windowManager.updateViewLayout(view, params)
    }

    /**
     * 请求重新吸边
     *
     * 当悬浮窗内容大小发生变化时调用，
     * 会等待 layout 完成后根据新尺寸重新计算吸附位置。
     */
    fun requestReSnap() {
        val view = rootView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val provider = touchProvider
        if (provider is SnapToEdgeTouchProvider) {
            provider.requestReSnap(view, params, windowManager)
        } else {
            clampToSafeBounds(postIfNeeded = true)
        }
    }

    private fun registerDisplayListener() {
        unregisterDisplayListener()
        displayManager?.registerDisplayListener(displayListener, mainHandler)
    }

    private fun unregisterDisplayListener() {
        displayManager?.unregisterDisplayListener(displayListener)
    }

    private fun currentScreenFingerprint(): String {
        val size = OverlayScreenBounds.screenSize(windowManager)
        val insets = OverlayScreenBounds.systemBarInsets(windowManager, context)
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        return "${size.x}x${size.y}:$rotation:${insets.left},${insets.top},${insets.right},${insets.bottom}"
    }

    private fun createLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int,
    ): WindowManager.LayoutParams {
        val type = when {
            context is AccessibilityService -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        }

        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(view: ComposeView, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
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
                    )
                    params.x = clampedX
                    params.y = clampedY
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        /** 检查是否有悬浮窗权限 */
        fun canDrawOverlays(context: Context = application): Boolean {
            return Settings.canDrawOverlays(context)
        }
    }
}

/**
 * 触摸拦截容器
 *
 * 通过 [onInterceptTouchEvent] 判断是否应该拦截（拖拽），
 * 不拦截时事件正常透传给子 Compose 组件（点击、滑动等）。
 */
private class OverlayInterceptLayout(
    context: Context,
    private val provider: OverlayTouchProvider,
    private val params: WindowManager.LayoutParams,
    private val wm: WindowManager,
) : FrameLayout(context) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return provider.onInterceptTouchEvent(ev, params, wm, this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return provider.onTouchEvent(event, params, wm, this)
    }
}

/**
 * 悬浮窗专用 LifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
        if (event == Lifecycle.Event.ON_DESTROY) {
            store.clear()
        }
    }
}
