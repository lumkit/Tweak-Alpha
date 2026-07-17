package io.github.lumkit.tweak.ui.screen.filePicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.Const
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.FileEntry
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_file
import tweak_alpha.shared.generated.resources.ic_folder
import tweak_alpha.shared.generated.resources.text_file_picker_confirm
import tweak_alpha.shared.generated.resources.text_file_picker_empty
import tweak_alpha.shared.generated.resources.text_file_picker_load_failed
import tweak_alpha.shared.generated.resources.text_file_picker_open_folder
import tweak_alpha.shared.generated.resources.text_file_picker_parent
import tweak_alpha.shared.generated.resources.text_file_picker_select_current_folder
import tweak_alpha.shared.generated.resources.text_file_picker_title_file
import tweak_alpha.shared.generated.resources.text_file_picker_title_files
import tweak_alpha.shared.generated.resources.text_file_picker_title_folder
import tweak_alpha.shared.generated.resources.text_file_picker_title_folders
import tweak_alpha.shared.generated.resources.text_go_back
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun FilePickerScreen(route: Screen.FilePicker) {
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current

    val rootPath = remember { Const.Path.externalStorage.trimEnd('/') }
    var currentPath by remember { mutableStateOf(rootPath) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var buttonHeight by remember { mutableStateOf(0.dp) }
    val delivered = remember { AtomicBoolean(false) }

    // 用户未确认直接返回时清除 pending，避免回调永久滞留
    DisposableEffect(route.requestId) {
        onDispose {
            if (!delivered.get()) {
                FilePickerResultRegistry.clearPending(route.requestId)
            }
        }
    }

    val title = stringResource(
        when (route.action) {
            FilePickerAction.File -> Res.string.text_file_picker_title_file
            FilePickerAction.Files -> Res.string.text_file_picker_title_files
            FilePickerAction.Folder -> Res.string.text_file_picker_title_folder
            FilePickerAction.Folders -> Res.string.text_file_picker_title_folders
        }
    )

    LaunchedEffect(currentPath) {
        loading = true
        errorMessage = null
        when (val result = Files.listEntries(currentPath)) {
            is NativeFileResult.Success -> {
                entries = result.value.sortedWith(
                    compareByDescending<FileEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            }

            is NativeFileResult.Failure -> {
                entries = emptyList()
                errorMessage = result.error.message
            }
        }
        loading = false
    }

    val canGoUp = remember(currentPath) {
        currentPath != "/" && currentPath.isNotBlank()
    }
    val currentSelected = currentPath in selectedPaths
    val confirmEnabled = selectedPaths.isNotEmpty()

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = title,
                    subTitle = currentPath,
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
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .layerBackdrop(backdrop)
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(direction) + 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        end = padding.calculateEndPadding(direction) + 16.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp + buttonHeight,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (route.action.selectsDirectory) {
                        item {
                            Card {
                                BasicComponent(
                                    title = stringResource(Res.string.text_file_picker_select_current_folder),
                                    summary = currentPath,
                                    endActions = {
                                        Checkbox(
                                            state = if (currentSelected) {
                                                ToggleableState.On
                                            } else {
                                                ToggleableState.Off
                                            },
                                            onClick = null,
                                        )
                                    },
                                    onClick = {
                                        selectedPaths = toggleSelection(
                                            current = selectedPaths,
                                            path = currentPath,
                                            multiSelect = route.action.isMultiSelect,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    if (canGoUp) {
                        item {
                            Card {
                                BasicComponent(
                                    title = stringResource(Res.string.text_file_picker_parent),
                                    startAction = {
                                        Icon(
                                            imageVector = MiuixIcons.ListView,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            modifier = Modifier.size(22.dp),
                                        )
                                    },
                                    endActions = {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    onClick = {
                                        currentPath = parentPath(currentPath) ?: currentPath
                                    },
                                )
                            }
                        }
                    }

                    when {
                        loading -> Unit
                        errorMessage != null -> {
                            item {
                                Text(
                                    text = stringResource(
                                        Res.string.text_file_picker_load_failed,
                                        errorMessage.orEmpty(),
                                    ),
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    style = MiuixTheme.textStyles.body2,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }

                        entries.isEmpty() -> {
                            item {
                                Text(
                                    text = stringResource(Res.string.text_file_picker_empty),
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    style = MiuixTheme.textStyles.body2,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        else -> {
                            items(
                                items = entries,
                                key = { it.path },
                            ) { entry ->
                                FilePickerEntryItem(
                                    entry = entry,
                                    action = route.action,
                                    selected = entry.path in selectedPaths,
                                    onOpenDirectory = { currentPath = entry.path },
                                    onToggle = {
                                        selectedPaths = toggleSelection(
                                            current = selectedPaths,
                                            path = entry.path,
                                            multiSelect = route.action.isMultiSelect,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (!confirmEnabled) return@Button
                        delivered.set(true)
                        FilePickerResultRegistry.deliver(
                            requestId = route.requestId,
                            paths = selectedPaths.toList().sorted(),
                        )
                        navigator.goBack()
                    },
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding() + 16.dp)
                        .padding(start = padding.calculateStartPadding(direction) + 16.dp)
                        .padding(end = padding.calculateEndPadding(direction) + 16.dp)
                        .fillMaxWidth()
                        .onSizeChanged {
                            buttonHeight = with(density) { it.height.toDp() }
                        },
                ) {
                    Text(text = stringResource(Res.string.text_file_picker_confirm))
                }
            }
        }
    }
}

@Composable
private fun FilePickerEntryItem(
    entry: FileEntry,
    action: FilePickerAction,
    selected: Boolean,
    onOpenDirectory: () -> Unit,
    onToggle: () -> Unit,
) {
    val selectable = if (action.selectsDirectory) entry.isDirectory else !entry.isDirectory

    Card {
        BasicComponent(
            title = entry.name,
            startAction = {
                Icon(
                    painter = if (entry.isDirectory) {
                        painterResource(Res.drawable.ic_folder)
                    } else {
                        painterResource(Res.drawable.ic_file)
                    },
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(24.dp),
                )
            },
            endActions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectable) {
                        Checkbox(
                            state = if (selected) ToggleableState.On else ToggleableState.Off,
                            onClick = null,
                        )
                    }
                    if (entry.isDirectory) {
                        IconButton(onClick = onOpenDirectory) {
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = stringResource(Res.string.text_file_picker_open_folder),
                                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            },
            onClick = {
                when {
                    selectable -> onToggle()
                    entry.isDirectory -> onOpenDirectory()
                }
            },
        )
    }
}

private fun toggleSelection(
    current: Set<String>,
    path: String,
    multiSelect: Boolean,
): Set<String> {
    return if (multiSelect) {
        if (path in current) current - path else current + path
    } else {
        if (path in current) emptySet() else setOf(path)
    }
}

private fun parentPath(path: String): String? {
    val normalized = path.trimEnd('/')
    if (normalized.isEmpty() || normalized == "/") return null
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return when {
        parent.isEmpty() -> "/"
        else -> parent
    }
}
