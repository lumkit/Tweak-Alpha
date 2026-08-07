package io.github.lumkit.tweak.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.ContextContent
import io.github.lumkit.tweak.common.base.BaseOverlayController
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.Block
import io.github.lumkit.tweak.common.component.LintStackChart
import io.github.lumkit.tweak.common.component.rememberChartState
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.ComposeOverlayHelper
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.CpuLoadUtils
import io.github.lumkit.tweak.common.utils.GpuUtils
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.fps.FpsUtils
import io.github.lumkit.tweak.common.utils.fps.SurfaceFlingerFpsUtil
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.service.OverlayService
import io.github.lumkit.tweak.shared.R
import io.github.lumkit.tweak.ui.screen.info.DeviceInfoViewModel
import io.github.lumkit.tweak.ui.theme.getJetBrainsMonoRegularFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

class MiniLoadWatcherOverlayController(
    fallbackContextProvider: () -> Context,
    accessibilityContextProvider: () -> Context?,
) : BaseOverlayController(
    fallbackContextProvider = fallbackContextProvider,
    accessibilityContextProvider = accessibilityContextProvider,
    tag = OverlayService.TAG,
) {
    override val overlayName: String = "miniLoadWatcherOverlay"

    override val draggable: Boolean
        get() = true

    override val startX: Int
        get() = TweakDataStore.miniLoadWatcherOverlayPosition?.first ?: super.startX

    override val startY: Int
        get() = TweakDataStore.miniLoadWatcherOverlayPosition?.second ?: super.startY

    private val expanded = MutableStateFlow(false)

    override fun onShowingChanged(isShowing: Boolean) {
        OverlayMonitor._miniLoadWatcherIsShowing.value = isShowing
        if (!isShowing) {
            expanded.value = false
        }
    }

    override fun createOverlayHelper(context: Context): ComposeOverlayHelper {
        val helper = ComposeOverlayHelper(context, false)
        return helper
    }

    @Composable
    override fun Content() {
        ContextContent(
            fontFamily = getJetBrainsMonoRegularFontFamily(),
        ) {
            Box(
                modifier = Modifier.clip(Rectangle.copy(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(color = Color(0x55555555))
            ) {
                MiniLoadContent()
            }
        }
    }
}

private class MiniLoadContentViewModel : BaseViewModel() {

    data class MiniLoadDetail(
        val cpuFreq: String,
        val cpuLoad: Float,
        val gpuFreq: String,
        val gpuLoad: Float,
        val fps: String,
        val batteryInfo: String,
    )

    private val _miniLoadDetail = MutableStateFlow(MiniLoadDetail("--", 0f, "--", 0f, "--", "--"))
    val miniLoadDetail = _miniLoadDetail.asStateFlow()

    private var tick = 0L

    private val cpuLoadUtils = CpuLoadUtils()
    private val cpuFrequencyUtil = CpuFrequencyUtil()
    private val batteryUtils = BatteryUtils
    private val gpuUtils = GpuUtils
    private val fpsUtils = FpsUtils

    private var clusters: List<List<String>> = emptyList()

    init {
        viewModelScope.launch {
            while (isActive) {
                launch(Dispatchers.Default) {
                    SurfaceFlingerFpsUtil.getCurrentFps()
                }
                updateDetail()
                tick++
                delay(GlobalViewModel.infoUpdateTimeSpanMillisecondsState.value.milliseconds)
            }
        }
    }

    private suspend fun updateDetail() {
        if (clusters.isEmpty()) {
            clusters = cpuFrequencyUtil.getClusterInfo()
        }

        val clusterFrequencies = List(clusters.size) { clusterIndex ->
            cpuFrequencyUtil.getCurrentFrequency(clusterIndex)
        }

        val cpuLoad = cpuLoadUtils.getCpuLoadSum().coerceAtLeast(0.0).toFloat()
        val maxFrequency = clusterFrequencies
            .mapNotNull { it.toLongOrNull() }
            .maxOrNull() ?: 0L
        val gpuLoad = GpuUtils.getGpuLoad()
            .takeIf { it >= 0 }
            ?.toFloat()?.div(100f)
        val gpuFreq = GpuUtils.getGpuFreq()
        val batterySnapshot = batteryUtils.getSnapshot()
        val batteryPower = batteryUtils.getPower()
        val temperatureCelsius = batterySnapshot.temperatureCelsius
        val fps = FpsUtils.getCurrentFps()
        val fpsText = "%.1f".format(fps)
        _miniLoadDetail.value = MiniLoadDetail(
            cpuFreq = DeviceInfoViewModel.formatFreq(maxFrequency.toString(), ""),
            cpuLoad = cpuLoad / 100f,
            gpuFreq = gpuFreq.ifEmpty { "--" },
            gpuLoad = gpuLoad ?: 0f,
            fps = fpsText ?: "--",
            batteryInfo = if ((tick % 5L) == 0L) {
                batteryPower?.formatPower() ?: "--"
            } else {
                temperatureCelsius?.let { "%.1f°C".format(it) } ?: "--"
            }
        )
    }
}

@Composable
private fun MiniLoadContent(
    viewModel: MiniLoadContentViewModel = viewModel { MiniLoadContentViewModel() }
) {
    val detail by viewModel.miniLoadDetail.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // CPU
        Block {
            val chartState = rememberChartState()

            LaunchedEffect(detail) {
                chartState.push(detail.cpuLoad)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_soc_mini),
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = Color.White,
                )

                LintStackChart(
                    modifier = Modifier.size(width = 20.dp, height = 9.dp),
                    state = chartState,
                    chartWith = 2.dp,
                )

                Text(
                    text = detail.cpuFreq,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 7.8.sp,
                        lineHeight = 7.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(24.dp)
                )
            }
        }

        // GPU
        Block {
            val chartState = rememberChartState()

            LaunchedEffect(detail) {
                chartState.push(detail.gpuLoad)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_gpu_mini),
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = Color.White,
                )

                LintStackChart(
                    modifier = Modifier.size(width = 12.dp, height = 9.dp),
                    state = chartState,
                    chartWith = 2.dp,
                )

                Text(
                    text = detail.gpuFreq,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 7.8.sp,
                        lineHeight = 7.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(22.dp)
                )
            }
        }

        // FPS
        Block {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_layers_mini),
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = Color.White,
                )

                Text(
                    text = detail.fps,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 7.8.sp,
                        lineHeight = 7.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(24.dp)
                )
            }
        }

        // 电池
        Block {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_battery_mini),
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = Color.White,
                )

                Text(
                    text = detail.batteryInfo,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 7.8.sp,
                        lineHeight = 7.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}