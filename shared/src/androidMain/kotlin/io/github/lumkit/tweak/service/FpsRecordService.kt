package io.github.lumkit.tweak.service

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.shapes.Capsule
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.ContextContent
import io.github.lumkit.tweak.MainActivity
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.base.BaseService
import io.github.lumkit.tweak.common.component.rememberTextWidth
import io.github.lumkit.tweak.common.utils.ComposeOverlayHelper
import io.github.lumkit.tweak.common.utils.OverlayScreenBounds
import io.github.lumkit.tweak.common.utils.SnapToEdgeTouchProvider
import io.github.lumkit.tweak.common.utils.ForegroundAppMonitor
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.fps.FpsUtils
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.model.NavigationIntent
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.fpsRecord.FpsRecordServiceViewModel
import io.github.lumkit.tweak.ui.theme.colorBusy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class FpsRecordService : BaseService() {

    override fun onBind(p0: Intent?): IBinder? = null

    companion object {
        const val TAG = "FpsRecordService"

        private const val ACTION_SHOW_RECORD_OVERLAY =
            "io.github.lumkit.tweak.action.SHOW_RECORD_OVERLAY"
        private const val ACTION_HIDE_RECORD_OVERLAY =
            "io.github.lumkit.tweak.action.HIDE_RECORD_OVERLAY"

        fun showRecordOverlay() {
            val intent = Intent(application, FpsRecordService::class.java)
            intent.action = ACTION_SHOW_RECORD_OVERLAY
            application.startService(intent)
        }

        fun hideRecordOverlay() {
            val intent = Intent(application, FpsRecordService::class.java)
            intent.action = ACTION_HIDE_RECORD_OVERLAY
            application.startService(intent)
        }
    }

    private val overlayHelper by lazy {
        val touchProvider = SnapToEdgeTouchProvider(
            context = this@FpsRecordService,
            edgePadding = (resources.displayMetrics.density * 8f + .5f).roundToInt()
        ).apply {
            onInteractionStart = {
                FpsRecordServiceViewModel.onOverlayInteractionStart()
            }
            onInteractionEnd = {
                FpsRecordServiceViewModel.onOverlayInteractionEnd()
            }
            onPositionSettled = { x, y ->
                serviceScope.launch {
                    TweakDataStore.setFpsOverlayPosition(x, y)
                }
            }
        }
        ComposeOverlayHelper(this).apply {
            setTouchProvider(touchProvider)
        }
    }

    private val displayManager by lazy {
        getSystemService(DisplayManager::class.java)
    }

    private val mainHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    private var lastScreenState: OverlayScreenState? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (!overlayHelper.isShowing) {
                lastScreenState = currentScreenState()
                return
            }
            val newState = currentScreenState()
            if (newState != lastScreenState) {
                lastScreenState = newState
                handleOverlayScreenChanged()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ForegroundAppMonitor.start()
        lastScreenState = currentScreenState()
        displayManager?.registerDisplayListener(displayListener, mainHandler)
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_SHOW_RECORD_OVERLAY -> {
                logD("ACTION_SHOW_RECORD_OVERLAY", TAG)
                if (!overlayHelper.isShowing) {
                    val saved = TweakDataStore.fpsOverlayPosition
                    val (startX, startY) = saved ?: getDefaultOverlayPosition()
                    overlayHelper.show(x = startX, y = startY) {
                        FpsRecordContent()
                    }
                    lastScreenState = currentScreenState()
                } else {
                    overlayHelper.dismiss()
                }
            }

            ACTION_HIDE_RECORD_OVERLAY -> {
                logD("ACTION_HIDE_RECORD_OVERLAY", TAG)
                if (overlayHelper.isShowing) {
                    overlayHelper.dismiss()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        displayManager?.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    /** 默认位置：屏幕右侧、距底部 150dp */
    private fun getDefaultOverlayPosition(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val bottomMarginPx = (175 * dm.density + 0.5f).toInt()
        return screenWidth to (screenHeight - bottomMarginPx)
    }

    private fun handleOverlayScreenChanged() {
        overlayHelper.clampToSafeBounds(postIfNeeded = true)
        overlayHelper.requestReSnap()
    }

    private fun currentScreenState(): OverlayScreenState {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val screen = OverlayScreenBounds.screenSize(windowManager)
        val insets = OverlayScreenBounds.systemBarInsets(windowManager, this)
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        return OverlayScreenState(
            width = screen.x,
            height = screen.y,
            rotation = rotation,
            insetLeft = insets.left,
            insetTop = insets.top,
            insetRight = insets.right,
            insetBottom = insets.bottom,
        )
    }

    private data class OverlayScreenState(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val insetLeft: Int,
        val insetTop: Int,
        val insetRight: Int,
        val insetBottom: Int,
    )
}

@Composable
private fun ComposeOverlayHelper.FpsRecordContent() {
    val viewModel = FpsRecordServiceViewModel
    val recordingState by viewModel.isRecordingState.collectAsStateWithLifecycle()
    val isImmersiveMode by viewModel.isImmersiveMode.collectAsStateWithLifecycle()
    var currentFps by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var invisibleState by remember { mutableStateOf(false) }

    LaunchedEffect(recordingState) {
        while (isActive) {
            currentFps = "%d".format(FpsUtils.getCurrentFps().roundToInt())
            delay(1000.milliseconds)
        }
    }

    LaunchedEffect(isImmersiveMode) {
        requestReSnap()
        if (isImmersiveMode) {
            delay(2000.milliseconds)
            if (!isActive) return@LaunchedEffect
            invisibleState = true
        } else {
            invisibleState = false
        }
    }

    ContextContent {
        Row(
            modifier = Modifier.clip(Capsule())
                .alpha(if (invisibleState) .45f else 1f)
                .background(MiuixTheme.colorScheme.onBackground.copy(.65f))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.background)
                    .padding(3.dp)
                    .clickable {
                        if (recordingState) {
                            viewModel.stopRecord()
                        } else {
                            viewModel.startRecord()
                        }
                        scope.launch {
                            delay(500.milliseconds)
                            requestReSnap()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = recordingState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) {
                    if (it) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(12.dp)
                                    .alpha(
                                        if (isImmersiveMode) {
                                            .31f
                                        } else {
                                            1f
                                        }
                                    )
                                    .clip(Rectangle.copy(cornerRadius = 3.dp))
                                    .background(MiuixTheme.colorScheme.error)
                            )
                            if (isImmersiveMode) {
                                Text(
                                    text = currentFps,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentFps,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.background
                            )
                        }
                    }
                }
            }

            if (!recordingState) {
                PreferenceContent(this@FpsRecordContent)
            } else {
                if (!isImmersiveMode) {
                    ClockContent()
                }
            }
        }
    }
}

@Composable
private fun PreferenceContent(helper: ComposeOverlayHelper) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = MiuixIcons.ListView,
            contentDescription = null,
            modifier = Modifier.clip(CircleShape)
                .size(22.dp)
                .padding(2.dp)
                .clickable {
                    val intentJson = NavigationIntent.encode(Screen.FpsRecord)

                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = ConstCommon.Navigation.ACTION_DEEPLINK_SELF
                        putExtra(ConstCommon.Navigation.EXTRA_NAV_INTENT, intentJson)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                    context.startActivity(intent)
                },
            tint = MiuixTheme.colorScheme.background.copy(.75f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = MiuixIcons.Close,
            contentDescription = null,
            modifier = Modifier.padding(end = 6.dp)
                .clip(CircleShape)
                .size(18.dp)
                .padding(2.dp)
                .clickable {
                    helper.dismiss()
                },
            tint = MiuixTheme.colorScheme.background.copy(.75f),
        )
    }
}

@Composable
private fun ClockContent() {
    val timeText by FpsRecordServiceViewModel.elapsedTimeText.collectAsStateWithLifecycle()
    val isSamplingPaused by FpsRecordServiceViewModel.isSamplingPaused.collectAsStateWithLifecycle()
    val textStyle = MiuixTheme.textStyles.footnote2
    val maxWidth = rememberTextWidth(textStyle, "FPS: 888")

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = timeText,
            style = textStyle,
            color = if (isSamplingPaused) {
                colorBusy
            } else {
                MiuixTheme.colorScheme.background
            },
            modifier = Modifier.width(maxWidth)
        )

        Spacer(modifier = Modifier.width(10.dp))
    }
}
