package io.github.lumkit.tweak.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.lumkit.tweak.common.utils.ComposeOverlayHelper
import io.github.lumkit.tweak.common.utils.ForegroundAppMonitor
import io.github.lumkit.tweak.common.utils.HandleDragTouchProvider
import io.github.lumkit.tweak.common.utils.ProcessUtilLite
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.ThreadInfo
import io.github.lumkit.tweak.service.OverlayService
import io.github.lumkit.tweak.ui.theme.getJetBrainsMonoRegularFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class ThreadWatcherOverlayController(
    contextProvider: () -> Context,
) : BaseOverlayController(
    contextProvider = contextProvider,
    tag = OverlayService.TAG,
) {

    override val overlayName: String = "threadWatcherOverlay"

    override val draggable: Boolean
        get() = true

    override val startX: Int
        get() = TweakDataStore.threadWatcherOverlayPosition?.first ?: super.startX

    override val startY: Int
        get() = TweakDataStore.threadWatcherOverlayPosition?.second ?: super.startY

    private val expanded = MutableStateFlow(false)
    private val controllerScope = CoroutineScope(Dispatchers.IO)

    override fun onShowingChanged(isShowing: Boolean) {
        OverlayMonitor._threadWatcherIsShowing.value = isShowing
        if (!isShowing) {
            expanded.value = false
        }
    }

    override fun createOverlayHelper(context: Context): ComposeOverlayHelper {
        val helper = ComposeOverlayHelper(context)

        val touchProvider = HandleDragTouchProvider(
            context = context,
            handleHeightPx = (context.resources.displayMetrics.density * 36f + .5f).roundToInt(),
        ).apply {
            onPositionSettled = { x, y ->
                controllerScope.launch {
                    TweakDataStore.setThreadWatcherOverlayPosition(x, y)
                }
            }
        }

        helper.setTouchProvider(touchProvider)
        return helper
    }

    @Composable
    override fun Content() {
        ContextContent(
            fontFamily = getJetBrainsMonoRegularFontFamily(),
        ) {
            Box(
                modifier = Modifier.clip(Rectangle.copy(cornerRadius = 5.dp))
                    .background(color = Color(0x55000000))
            ) {
                ThreadBox()
            }
        }
    }
}

private class ThreadOverlayViewModel : BaseViewModel() {
    private val _topPackage = MutableStateFlow("")
    val topPackage = _topPackage.asStateFlow()

    private val _threads = MutableStateFlow<List<ThreadInfo>>(emptyList())
    val threads = _threads.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private var refreshJob: Job? = null

    init {
        startWatching()
    }

    private fun startWatching() {
        if (refreshJob?.isActive == true) {
            return
        }
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                refreshOnce()
                delay(GlobalViewModel.processInfoUpdateTimeState.value.milliseconds)
            }
        }
    }

    private suspend fun refreshOnce() {
        if (!ForegroundAppMonitor.isRunning.value) {
            _topPackage.value = ""
            _threads.value = emptyList()
            _message.value = "前台应用监听未就绪"
            _loading.value = false
            return
        }

        val packageName = ForegroundAppMonitor.currentForegroundPackage?.trim().orEmpty()
        if (packageName.isBlank()) {
            _topPackage.value = ""
            _threads.value = emptyList()
            _message.value = "暂无前台应用"
            _loading.value = false
            return
        }

        _topPackage.value = packageName
        _message.value = null
        val pid = ProcessUtilLite.getAppMainProcess(packageName)
        if (pid <= 0) {
            _threads.value = emptyList()
            _message.value = "暂无线程数据"
            _loading.value = false
            return
        }

        val threadLoads = runCatching {
            ProcessUtilLite.getThreadLoads(pid)
        }.getOrDefault(emptyList())

        _threads.value = threadLoads
            .sortedByDescending { it.cpuLoad }
            .take(15)
        _message.value = if (_threads.value.isEmpty()) "暂无线程数据" else null
        _loading.value = false
    }

    override fun onCleared() {
        refreshJob?.cancel()
        refreshJob = null
        super.onCleared()
    }

}

@Composable
private fun ThreadBox() {
    val viewModel: ThreadOverlayViewModel = viewModel { ThreadOverlayViewModel() }
    val topPackage by viewModel.topPackage.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.width(150.dp),
    ) {
        HeadContent(
            topPackage = topPackage.ifBlank { message ?: "暂无前台应用" },
            onClose = {
                OverlayMonitor.hideThreadWatcherOverlay()
            },
        )
        ThreadList(
            loading = loading,
            threads = threads,
            message = message,
        )
    }
}

@Composable
private fun HeadContent(
    topPackage: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = buildString {
                    append(topPackage)
                },
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                ),
                color = Color.White,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(),
            )

            Text(
                text = buildString {
                    append("CPU% Top15")
                },
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                ),
                color = Color.White,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = MiuixIcons.Close,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
                .padding(10.dp)
                .clickable(
                    indication = null,
                    interactionSource = null
                ) {
                    onClose()
                },
            tint = Color.White,
        )
    }
}

@Composable
private fun ThreadList(
    loading: Boolean,
    threads: List<ThreadInfo>,
    message: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CPU%",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = "CPUS",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = "TID",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(28.dp),
        )

        Text(
            text = "COMM",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f)
                .basicMarquee(),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
            .sizeIn(maxHeight = 100.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        when {
            loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
            }

            threads.isEmpty() -> {
                item {
                    Text(
                        text = message ?: "暂无线程数据",
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = 7.sp,
                            lineHeight = 7.sp,
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            else -> {
                items(items = threads) { thread ->
                    ThreadItem(
                        cpuLoad = "${thread.cpuLoad}",
                        cpus = thread.cpusAllowedList,
                        tid = thread.tid.toString(),
                        common = thread.name,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadItem(
    cpuLoad: String,
    cpus: String,
    tid: String,
    common: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = cpuLoad,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = cpus,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = tid,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(28.dp),
        )

        Text(
            text = common,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f)
                .basicMarquee(),
        )
    }
}
