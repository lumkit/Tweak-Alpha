package io.github.lumkit.tweak.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.ContextContent
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.base.BaseOverlayController
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.ComposeOverlayHelper
import io.github.lumkit.tweak.common.utils.CpuFrequencyUtil
import io.github.lumkit.tweak.common.utils.CpuLoadUtils
import io.github.lumkit.tweak.common.utils.DeviceMemoryInfoModel
import io.github.lumkit.tweak.common.utils.DeviceMemoryInfoUtils
import io.github.lumkit.tweak.common.utils.DeviceTemperatureUtils
import io.github.lumkit.tweak.common.utils.DragTouchProvider
import io.github.lumkit.tweak.common.utils.FpsUtils
import io.github.lumkit.tweak.common.utils.GpuUtils
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.animatedColorAsBattery
import io.github.lumkit.tweak.common.utils.animatedColorAsUsed
import io.github.lumkit.tweak.common.utils.formatPower
import io.github.lumkit.tweak.common.utils.getDeviceScreenRefreshRate
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.ui.screen.info.DeviceInfoViewModel
import io.github.lumkit.tweak.ui.theme.getJetBrainsMonoRegularFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class LoadWatcherOverlayController(
    fallbackContextProvider: () -> Context,
    accessibilityContextProvider: () -> Context?,
) : BaseOverlayController(
    fallbackContextProvider = fallbackContextProvider,
    accessibilityContextProvider = accessibilityContextProvider,
    tag = OverlayService.TAG,
) {

    override val overlayName: String = "loadWatcherOverlay"

    override val draggable: Boolean
        get() = true

    override val startX: Int
        get() = TweakDataStore.loadWatcherOverlayPosition?.first ?: super.startX

    override val startY: Int
        get() = TweakDataStore.loadWatcherOverlayPosition?.second ?: super.startY

    private val expanded = MutableStateFlow(false)
    private val controllerScope = CoroutineScope(Dispatchers.IO)

    override fun onShowingChanged(isShowing: Boolean) {
        OverlayMonitor._loadWatcherIsShowing.value = isShowing
        if (!isShowing) {
            expanded.value = false
        }
    }

    override fun createOverlayHelper(context: Context): ComposeOverlayHelper {
        val touchProvider = DragTouchProvider(context = context).apply {
            onClick = {
                expanded.value = !expanded.value
            }
            onDoubleClick = {
                OverlayMonitor.hideLoadWatcherOverlay()
            }
            onPositionSettled = { x, y ->
                controllerScope.launch {
                    TweakDataStore.setLoadWatcherOverlayPosition(x, y)
                }
            }
        }
        return ComposeOverlayHelper(context).apply {
            setTouchProvider(touchProvider)
        }
    }

    @Composable
    override fun Content() {
        val viewModel = viewModel { LoadWatcherViewModel() }
        val bundle by viewModel.bundle.collectAsStateWithLifecycle()
        val expanded by expanded.collectAsStateWithLifecycle()

        ContextContent {
            Box(
                modifier = Modifier.clip(Rectangle.copy(cornerRadius = 5.dp))
                    .background(color = Color(0x55000000))
            ) {
                bundle?.also {
                    if (expanded) {
                        ExpandedContent(it)
                    } else {
                        HorizontalChartsContent(it)
                    }
                } ?: Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InfiniteProgressIndicator()
                }
            }
        }
    }
}

class LoadWatcherViewModel : BaseViewModel() {
    companion object {
        private const val INFO_LABEL_WIDTH = 6
    }


    data class Bundle(
        val cpuLoad: Float,
        val maxFreq: String,
        val gpuLoad: Float?,
        val gpuFreq: String?,
        val batteryLevel: Int,
        val batteryStatus: Int,
        val batteryTemperature: Float,
        val moreInfo: AnnotatedString,
        val fpsText: String?,
        val fpsRatio: Float?,
    )

    private val _bundle = MutableStateFlow<Bundle?>(null)
    val bundle = _bundle.asStateFlow()

    private val cpuLoadUtils = CpuLoadUtils()
    private val cpuFrequencyUtil = CpuFrequencyUtil()
    private var clusters: List<List<String>> = emptyList()

    init {
        viewModelScope.launch {
            while (isActive) {
                updateBundle()
                delay(GlobalViewModel.infoUpdateTimeSpanMillisecondsState.value.milliseconds)
            }
        }
    }

    private suspend fun updateBundle() {
        if (clusters.isEmpty()) {
            clusters = cpuFrequencyUtil.getClusterInfo()
        }

        val clusterFrequencies = List(clusters.size) { clusterIndex ->
            cpuFrequencyUtil.getCurrentFrequency(clusterIndex)
        }
        val loads = cpuLoadUtils.getCpuLoad()
        val cpuLoad = cpuLoadUtils.getCpuLoadSum().coerceAtLeast(0.0).toFloat()
        val maxFrequency = clusterFrequencies
            .mapNotNull { it.toLongOrNull() }
            .maxOrNull() ?: 0L
        val gpuLoad = GpuUtils.getGpuLoad()
            .takeIf { it >= 0 }
            ?.toFloat()?.div(100f)
        val gpuFreq = "${GpuUtils.getGpuFreq()}MHz"
        val batterySnapshot = BatteryUtils.getSnapshot()
        val batteryPower = BatteryUtils.getPower()
        val batteryStatus = resolveBatteryStatus()
        val memoryInfo = DeviceMemoryInfoUtils.getMemoryInfo()
        val memoryFreq = DeviceMemoryInfoUtils.getMemoryFreq()
        val cpuTemperature = DeviceTemperatureUtils.getAverageCpuTemperature()
        val gpuMemoryUsage = GpuUtils.getMemoryUsage()
        val fps = FpsUtils.getCurrentFps()
        val fpsText = fps.takeIf { it > 0f }?.let { "%.1f".format(it) }
        val moreInfo = buildMoreInfo(
            memoryInfo = memoryInfo,
            memoryFreq = memoryFreq,
            cpuTemperature = cpuTemperature,
            gpuMemoryUsage = gpuMemoryUsage,
            clusters = clusters,
            clusterFrequencies = clusterFrequencies,
            loads = loads,
            fpsText = fpsText,
            batteryPower = batteryPower,
        )

        _bundle.value = Bundle(
            cpuLoad = cpuLoad / 100f,
            maxFreq = DeviceInfoViewModel.formatFreq(maxFrequency.toString()),
            gpuLoad = gpuLoad,
            gpuFreq = gpuFreq,
            batteryLevel = batterySnapshot.capacityPercent ?: 0,
            batteryStatus = batteryStatus,
            batteryTemperature = batterySnapshot.temperatureCelsius ?: 0f,
            moreInfo = moreInfo,
            fpsText = fpsText,
            fpsRatio = fps / getDeviceScreenRefreshRate(),
        )
    }

    private fun resolveBatteryStatus(): Int {
        val batteryIntent = application.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return BatteryManager.BATTERY_STATUS_UNKNOWN
        return batteryIntent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        )
    }

    private fun buildMoreInfo(
        memoryInfo: DeviceMemoryInfoModel,
        memoryFreq: Long?,
        cpuTemperature: Float?,
        gpuMemoryUsage: String?,
        clusters: List<List<String>>,
        clusterFrequencies: List<String>,
        loads: Map<Int, Double>,
        fpsText: String?,
        batteryPower: Int?,
    ): AnnotatedString {
        return buildAnnotatedString {
            val usedMemory = (memoryInfo.memTotal - memoryInfo.memAvailable).coerceAtLeast(0L)
            val ramUsagePercent = if (memoryInfo.memTotal > 0L) {
                (usedMemory * 100 / memoryInfo.memTotal).toInt()
            } else {
                0
            }
            appendInfoLine("#RAM", "$ramUsagePercent%", highlighted = true)
            memoryFreq?.takeIf { it > 0L }?.let {
                append('\n')
                appendInfoLine(" DDR", "${it}Mhz")
            }
            append('\n')
            appendInfoLine("#CTMP", cpuTemperature.toDisplayTemperature(), highlighted = true)
            append('\n')

            gpuMemoryUsage?.let {
                appendInfoLine("#GMEM", it, highlighted = true)
                append('\n')
            }

            clusters.forEachIndexed { clusterIndex, cluster ->
                if (clusterIndex != 0) {
                    append('\n')
                }
                if (cluster.isEmpty()) {
                    return@forEachIndexed
                }

                val title = buildString {
                    append('#')
                    append(cluster.first())
                    append('~')
                    append(cluster.last())
                }
                appendInfoLine(
                    label = title,
                    value = "${clusterFrequencies.getOrNull(clusterIndex).toDisplayMHz()}Mhz",
                    highlighted = true,
                )

                cluster.forEach { core ->
                    append('\n')
                    val label = " CPU$core"
                    val load = loads[core.toIntOrNull() ?: -1]
                    if (load != null) {
                        val loadInt = load.roundToInt().coerceAtLeast(0)
                        val value = buildString {
                            if (loadInt < 10) {
                                append(' ')
                            }
                            append(loadInt)
                            append('%')
                        }
                        appendInfoLine(label, value)
                    } else {
                        appendInfoLine(label, "×")
                    }
                }
            }

            fpsText?.let {
                append('\n')
                appendInfoLine("#FPS", it, highlighted = true)
            }

            batteryPower?.let {
                append('\n')
                appendInfoLine("#PWR", it.formatPower(), highlighted = true)
            }
        }
    }

    private fun AnnotatedString.Builder.appendHighlightLine(text: String) {
        pushStyle(
            SpanStyle(
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        )
        append(text)
        pop()
    }

    private fun AnnotatedString.Builder.appendInfoLine(
        label: String,
        value: String,
        highlighted: Boolean = false,
    ) {
        val text = buildString {
            append(label.padEnd(INFO_LABEL_WIDTH))
            append(' ')
            append(value)
        }
        if (highlighted) {
            appendHighlightLine(text)
        } else {
            append(text)
        }
    }

    private fun Long.normalizeKHzToMhz(): Long {
        return if (this >= 1000L) this / 1000L else this
    }

    private fun String?.toDisplayMHz(): String {
        val value = this?.toLongOrNull() ?: return "0"
        return value.normalizeKHzToMhz().toString()
    }

    private fun Float?.toDisplayTemperature(): String {
        val value = this ?: 0f
        return "${"%.1f".format(value)}℃"
    }
}

@Composable
private fun HorizontalChartsContent(bundle: LoadWatcherViewModel.Bundle) {
    Row(
        modifier = Modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ChartsContent(bundle)
    }
}

@Composable
private fun ExpandedContent(bundle: LoadWatcherViewModel.Bundle) {
    Row(
        modifier = Modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ChartsContent(bundle)
        }

        Text(
            text = bundle.moreInfo,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 10.2.sp,
                fontFamily = getJetBrainsMonoRegularFontFamily(),
            ),
            color = Color(0xbbffffff),
            modifier = Modifier.width(68.dp)
        )
    }
}

@Composable
private fun ChartsContent(bundle: LoadWatcherViewModel.Bundle) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(35.dp),
            contentAlignment = Alignment.Center,
        ) {
            val loadColor by animatedColorAsUsed(bundle.cpuLoad, alpha = 1f)
            CircularProgressIndicator(
                modifier = Modifier.padding(2.dp)
                    .fillMaxSize(),
                progress = bundle.cpuLoad,
                strokeWidth = 4.5f.dp,
                size = 31.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    backgroundColor = Color(0x22FFFFFF),
                    foregroundColor = loadColor,
                ),
            )

            Text(
                text = "CPU",
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                    fontFamily = getJetBrainsMonoRegularFontFamily(),
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
        }

        Text(
            text = bundle.maxFreq,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 5.6.sp,
                lineHeight = 5.6.sp,
                fontFamily = getJetBrainsMonoRegularFontFamily(),
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(35.dp),
            contentAlignment = Alignment.Center,
        ) {
            val loadColor by animatedColorAsUsed(bundle.gpuLoad ?: 0f, alpha = 1f)
            CircularProgressIndicator(
                modifier = Modifier.padding(2.dp)
                    .fillMaxSize(),
                progress = bundle.gpuLoad ?: 0f,
                strokeWidth = 4.5f.dp,
                size = 31.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    backgroundColor = Color(0x22FFFFFF),
                    foregroundColor = loadColor,
                ),
            )

            Text(
                text = "GPU",
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                    fontFamily = getJetBrainsMonoRegularFontFamily(),
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
        }

        Text(
            text = bundle.gpuFreq ?: "--",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 5.6.sp,
                lineHeight = 5.6.sp,
                fontFamily = getJetBrainsMonoRegularFontFamily(),
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(35.dp),
            contentAlignment = Alignment.Center,
        ) {
            val load = remember(bundle) { bundle.batteryLevel / 100f  }
            val loadColor by animatedColorAsBattery(load, alpha = 1f)
            CircularProgressIndicator(
                modifier = Modifier.padding(2.dp)
                    .fillMaxSize(),
                progress = load,
                strokeWidth = 4.5f.dp,
                size = 31.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = loadColor,
                    backgroundColor = Color(0x22FFFFFF),
                ),
            )

            val charging = remember(bundle) { bundle.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING }

            Text(
                text = buildString {
                    append("${bundle.batteryLevel}%")
                    if (charging) append("+")
                },
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                    fontFamily = getJetBrainsMonoRegularFontFamily(),
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
        }

        Text(
            text = "%.1f℃".format(bundle.batteryTemperature),
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 5.6.sp,
                lineHeight = 5.6.sp,
                fontFamily = getJetBrainsMonoRegularFontFamily(),
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
        )
    }

    bundle.fpsText?.let { fpsText ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(35.dp),
                contentAlignment = Alignment.Center,
            ) {
                val loadColor by animatedColorAsBattery(bundle.fpsRatio ?: 0f, alpha = 1f)
                CircularProgressIndicator(
                    modifier = Modifier.padding(2.dp)
                        .fillMaxSize(),
                    progress = bundle.fpsRatio ?: 0f,
                    strokeWidth = 4.5f.dp,
                    size = 31.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = loadColor,
                        backgroundColor = Color(0x22FFFFFF),
                    ),
                )

                Text(
                    text = "FPS",
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 7.sp,
                        lineHeight = 7.sp,
                        fontFamily = getJetBrainsMonoRegularFontFamily(),
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
            }

            Text(
                text = fpsText,
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 5.6.sp,
                    lineHeight = 5.6.sp,
                    fontFamily = getJetBrainsMonoRegularFontFamily(),
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
        }
    }
}
