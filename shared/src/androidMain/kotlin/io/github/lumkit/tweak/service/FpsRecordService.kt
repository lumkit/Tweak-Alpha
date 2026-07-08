package io.github.lumkit.tweak.service

import android.content.Intent
import android.os.IBinder
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.shapes.Capsule
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.ContextContent
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.base.BaseService
import io.github.lumkit.tweak.common.utils.ComposeOverlayHelper
import io.github.lumkit.tweak.common.utils.ForegroundAppMonitor
import io.github.lumkit.tweak.common.utils.FpsUtils
import io.github.lumkit.tweak.common.utils.SnapToEdgeTouchProvider
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
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

class FpsRecordService: BaseService() {

    override fun onBind(p0: Intent?): IBinder? = null

    companion object {
        const val TAG = "FpsRecordService"

        private const val ACTION_SHOW_RECORD_OVERLAY = "io.github.lumkit.tweak.action.SHOW_RECORD_OVERLAY"
        private const val ACTION_HIDE_RECORD_OVERLAY = "io.github.lumkit.tweak.action.HIDE_RECORD_OVERLAY"

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

    override fun onCreate() {
        super.onCreate()

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

    /** 默认位置：屏幕右侧、距底部 150dp */
    private fun getDefaultOverlayPosition(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val bottomMarginPx = (175 * dm.density + 0.5f).toInt()
        return screenWidth to (screenHeight - bottomMarginPx)
    }
}

@Composable
private fun ComposeOverlayHelper.FpsRecordContent() {
    val viewModel = FpsRecordServiceViewModel
    val recordingState by viewModel.isRecordingState.collectAsStateWithLifecycle()
    var currentFps by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentFps = "%d".format(FpsUtils.getCurrentFps(ForegroundAppMonitor.currentForegroundPackage).roundToInt())
            delay(1000.milliseconds)
        }
    }

    ContextContent {
        Row(
            modifier = Modifier.clip(Capsule())
                .background(MiuixTheme.colorScheme.onBackground.copy(.65f))
                .padding(6.dp)
                .onSizeChanged {
                    // 内容大小变化时重新吸边
                    requestReSnap()
                },
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
                                    .clip(Rectangle.copy(cornerRadius = 3.dp))
                                    .background(MiuixTheme.colorScheme.error)
                            )
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
                ClockContent()
            }
        }
    }
}

@Composable
private fun PreferenceContent(helper: ComposeOverlayHelper) {
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
                    // TODO 启动某一Route
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
    val currentFps by FpsRecordServiceViewModel.currentFpsState.collectAsStateWithLifecycle()
    val isSamplingPaused by FpsRecordServiceViewModel.isSamplingPaused.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = buildString {
                append("FPS: $currentFps\n")
                append(timeText)
            },
            style = MiuixTheme.textStyles.footnote2,
            color = if (isSamplingPaused) {
                colorBusy
            } else {
                MiuixTheme.colorScheme.background
            }
        )

        Spacer(modifier = Modifier.width(10.dp))
    }
}
