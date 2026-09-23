package io.github.lumkit.tweak.ui.screen.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.crash.CrashLogEntry
import io.github.lumkit.tweak.common.crash.CrashLogSource
import io.github.lumkit.tweak.common.crash.CrashLogStore
import io.github.lumkit.tweak.common.utils.formatDateTime
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_crash_history
import tweak_alpha.shared.generated.resources.text_crash_history_empty
import tweak_alpha.shared.generated.resources.text_crash_source_client
import tweak_alpha.shared.generated.resources.text_crash_source_daemon
import tweak_alpha.shared.generated.resources.text_go_back

class CrashHistoryViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<CrashLogEntry>>(emptyList())
    val items = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _detail = MutableStateFlow("")
    val detail = _detail.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = runCatching { CrashLogStore.list() }.getOrDefault(emptyList())
            _loading.value = false
        }
    }

    fun loadDetail(path: String) {
        _detail.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            _detail.value = runCatching { CrashLogStore.read(path) }.getOrDefault("")
        }
    }
}

@Composable
fun CrashHistoryScreen(
    viewModel: CrashHistoryViewModel = viewModel { CrashHistoryViewModel() },
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<CrashLogEntry?>(null) }
    val navigator = LocalNavigator.current
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.text_crash_history),
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    navigationIcon = {
                        IconButton(onClick = navigator::goBack) {
                            Icon(
                                MiuixIcons.Back,
                                contentDescription = stringResource(Res.string.text_go_back),
                            )
                        }
                    },
                )
            },
            containerColor = MiuixTheme.colorScheme.surface,
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + 16.dp,
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(direction) + 16.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!loading && items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.text_crash_history_empty),
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
                items(items, key = { it.path }) { entry ->
                    CrashLogRow(entry) {
                        selected = entry
                        viewModel.loadDetail(entry.path)
                    }
                }
            }
        }
        CrashLogDetailSheet(
            entry = selected,
            text = detail,
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun CrashLogRow(
    entry: CrashLogEntry,
    onClick: () -> Unit,
) {
    val tag = when (entry.source) {
        CrashLogSource.DAEMON -> stringResource(Res.string.text_crash_source_daemon)
        CrashLogSource.CLIENT -> stringResource(Res.string.text_crash_source_client)
    }
    val fileName = entry.path.substringAfterLast('/')
    val description = buildString {
        append(formatDateTime(entry.timestamp, "yyyy-MM-dd"))
        append(' ')
        append(formatDateTime(entry.timestamp, "HH:mm:ss"))
        append(" · ")
        append(tag)
        if (entry.preview.isNotBlank()) {
            append('\n')
            append(entry.preview)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Rectangle.copy(cornerRadius = 16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            endActions = {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            },
        ) {
            Text(
                text = fileName,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.31f),
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CrashLogDetailSheet(
    entry: CrashLogEntry?,
    text: String,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = entry != null,
        title = entry?.path?.substringAfterLast('/').orEmpty(),
        onDismissRequest = onDismiss,
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
