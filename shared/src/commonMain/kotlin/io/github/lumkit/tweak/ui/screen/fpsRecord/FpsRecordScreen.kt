package io.github.lumkit.tweak.ui.screen.fpsRecord

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.common.utils.rememberRequestOverlayPermission
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_platform_device_style
import tweak_alpha.shared.generated.resources.ic_record_chart
import tweak_alpha.shared.generated.resources.ic_soc
import tweak_alpha.shared.generated.resources.ic_system_version
import tweak_alpha.shared.generated.resources.text_dialog_confirm
import tweak_alpha.shared.generated.resources.text_dialog_delete_fps_record_notes
import tweak_alpha.shared.generated.resources.text_dialog_delete_fps_record_notes_loading
import tweak_alpha.shared.generated.resources.text_dialog_tip
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_fps_recording
import tweak_alpha.shared.generated.resources.text_fps_recording_description
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_platform_model
import tweak_alpha.shared.generated.resources.text_platform_type
import tweak_alpha.shared.generated.resources.text_platform_version
import tweak_alpha.shared.generated.resources.text_record_table
import tweak_alpha.shared.generated.resources.text_record_table_description
import tweak_alpha.shared.generated.resources.text_recording_avg
import tweak_alpha.shared.generated.resources.text_recording_duration

internal val FpsRecordingProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "FpsRecordingProvider",
            title = Res.string.text_fps_recording,
            icon = Res.drawable.ic_record_chart,
            description = Res.string.text_fps_recording_description,
            capabilities = setOf(
                Capability.ROOT_ONLY,
            ),
            route = Screen.FpsRecord,
            defaultState = FeatureState.ENABLED,
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            }
        )

    @Composable
    override fun Content() {
        FpsRecordContent()
    }

}

@Composable
private fun FpsRecordContent() {
    val viewModel = FpsRecordViewModel
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backdrop = rememberLayerBackdropColor()
    val direction = LocalLayoutDirection.current
    val sessionNotes by viewModel.sessionNotes.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    var floatActionBarHeight by remember { mutableStateOf(0.dp) }
    val recordingState by FpsRecordServiceViewModel.isRecordingState.collectAsStateWithLifecycle()
    val actionState = rememberSaveable { mutableStateOf(false) }
    var loadingState by rememberSaveable { mutableStateOf(false) }

    // 悬浮窗权限申请：有权限直接显示，无权限跳转设置页，返回后自动检查
    val requestOverlay = rememberRequestOverlayPermission {
        showRecordOverlay()
    }

    LaunchedEffect(actionState.value) {
        if (!actionState.value) {
            viewModel.clearSelectedSessions()
        }
    }

    LaunchedEffect(sessionNotes) {
        if (sessionNotes.isEmpty()) {
            actionState.value = false
        }
    }

    viewModel.LoadStateLaunchEffect {
        Watch("softDeleteSessions", false) {
            val isLoading = it is BaseViewModel.LoadState.Loading
            loadingState = isLoading
        }
    }

    WindowDialog(
        show = loadingState,
        enableWindowDim = true,
        onDismissRequest = {

        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfiniteProgressIndicator()

            Text(
                text = stringResource(Res.string.text_dialog_delete_fps_record_notes_loading),
                style = MiuixTheme.textStyles.main
            )
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.text_fps_recording),
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (actionState.value) {
                                actionState.value = false
                            } else {
                                navigator.goBack()
                            }
                        }
                    ) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(Res.string.text_go_back),
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
        floatingToolbar = {
            FloatActionBar(
                actionState = actionState,
                recordingState = recordingState,
                requestOverlay = requestOverlay,
                onSizeChanged = {
                    floatActionBarHeight = with(density) {
                        it.height.toDp()
                    }
                }
            )
        },
        floatingToolbarPosition = ToolbarPosition.BottomEnd
    ) {
        LazyColumn(
            modifier = Modifier.layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = it.calculateStartPadding(direction) + 16.dp,
                top = it.calculateTopPadding() + 16.dp,
                end = it.calculateEndPadding(direction) + 16.dp,
                bottom = it.calculateBottomPadding() + 16.dp + floatActionBarHeight
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            item {
                InfoContent(viewModel)
            }

            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                RecordHead(sessionNotes)
            }

            items(
                items = sessionNotes,
                key = { it.sessionId }
            ) { noteVo ->
                RecordItem(noteVo, actionState) { sessionId ->
                    navigator.navigate(Screen.FpsRecordDetail(sessionId))
                }
            }
        }
    }
}


@Composable
private fun InfoContent(viewModel: FpsRecordViewModel) {
    val platformInfo by viewModel.platformInfoState.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoItem(
            title = stringResource(Res.string.text_platform_type),
            icon = Res.drawable.ic_platform_device_style,
            value = platformInfo?.platformName ?: "--",
        )

        InfoItem(
            title = stringResource(Res.string.text_platform_model),
            icon = Res.drawable.ic_soc,
            value = platformInfo?.deviceModel ?: "--",
        )

        InfoItem(
            title = stringResource(Res.string.text_platform_version),
            icon = Res.drawable.ic_system_version,
            value = platformInfo?.platformVersionName ?: "--",
        )
    }
}

@Composable
private fun RowScope.InfoItem(
    title: String,
    icon: DrawableResource,
    value: String,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
            style = MiuixTheme.textStyles.footnote2,
        )

        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
        )

        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable()
                .basicMarquee()
        )
    }
}

@Composable
private fun RecordHead(
    sessions: List<FpsRecordViewModel.FpsSessionNoteVo>,
) {
    Column{
        SmallTitle(text = stringResource(Res.string.text_record_table))

        AnimatedVisibility(
            visible = sessions.isEmpty(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(Res.string.text_record_table_description),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.clip(
                        Rectangle.copy(16.dp)
                    ).clickable {

                    }.padding(16.dp)
                )

            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun RecordItem(
    noteVo: FpsRecordViewModel.FpsSessionNoteVo,
    actionState: State<Boolean>,
    onTap: (id: Long) -> Unit
) {
    val selectedSessionIds by FpsRecordViewModel.selectedSessionIds.collectAsStateWithLifecycle()
    val selected = noteVo.sessionId in selectedSessionIds

    Card {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            startAction = {
                AsyncImage(
                    model = noteVo.appIconPath,
                    contentDescription = null,
                    error = null,
                    modifier = Modifier.clip(Rectangle.copy(cornerRadius = 12.dp))
                        .size(48.dp)
                )
            },
            endActions = {
                if (!actionState.value) {
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface.copy(.75f),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Checkbox(
                        state = if (selected) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        },
                        onClick = null
                    )
                }
            },
            onClick = {
                if (actionState.value) {
                    FpsRecordViewModel.toggleSelectedSession(noteVo.sessionId)
                } else {
                    onTap(noteVo.sessionId)
                }
            }
        ) {
            Text(
                text = noteVo.appName,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
            )

            Text(
                modifier = Modifier.basicMarquee(),
                text = buildString {
                    append(noteVo.createTime)
                    append("    ")
                    append(stringResource(Res.string.text_recording_duration))
                    append(noteVo.recordingDuration)
                    append("\n")
                    append(noteVo.avgFps)
                    append(stringResource(Res.string.text_recording_avg))
                    append("    ")
                    append(noteVo.avgPower)
                    append(stringResource(Res.string.text_recording_avg))
                },
                color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                style = MiuixTheme.textStyles.footnote2,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun FloatActionBar(
    actionState: MutableState<Boolean>,
    recordingState: Boolean,
    requestOverlay: () -> Unit,
    onSizeChanged: (IntSize) -> Unit,
) {

    BackHandler(actionState.value) {
        actionState.value = false
    }

    FloatingToolbar(
        modifier = Modifier.onSizeChanged {
            onSizeChanged(it)
        }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
        ) {
            val addAlpha by animateFloatAsState(targetValue = if (recordingState) .31f else 1f)
            val addRotation by animateFloatAsState(targetValue = if (recordingState) 45f else 0f)

            IconButton(
                onClick = {
                    requestOverlay()
                },
                enabled = !recordingState,
                modifier = Modifier.alpha(addAlpha)
                    .rotate(addRotation)
            ) {
                Icon(MiuixIcons.Add, contentDescription = null)
            }

            AnimatedVisibility(
                !actionState.value,
            ) {
                Row {
                    IconButton(
                        onClick = {
                            actionState.value = !actionState.value
                        }
                    ) {
                        Icon(MiuixIcons.Tune, contentDescription = null)
                    }
                }
            }

            AnimatedVisibility(
                actionState.value,
            ) {
                Actions(actionState)
            }
        }
    }
}

@Composable
private fun Actions(actionState: MutableState<Boolean>) {
    val selectedSessions by FpsRecordViewModel.selectedSessionIds.collectAsStateWithLifecycle()
    val hasSelectedAll by FpsRecordViewModel.hasSelectedAllSessions.collectAsStateWithLifecycle()
    val parentState = when {
        selectedSessions.isEmpty() -> ToggleableState.Off
        hasSelectedAll -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    val alphaAnimation by animateFloatAsState(targetValue = if (selectedSessions.isNotEmpty()) 1f else .31f)
    var showDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        IconButton(
            onClick = {
                FpsRecordViewModel.toggleSelectedAllSession()
            }
        ) {
            Checkbox(
                state = parentState,
                onClick = null
            )
        }

        IconButton(
            onClick = {
                showDialog = true
            },
            enabled = selectedSessions.isNotEmpty(),
            modifier = Modifier.alpha(alphaAnimation)
        ) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = null
            )
        }

        IconButton(
            onClick = {
                actionState.value = false
            }
        ) {
            Icon(
                imageVector = MiuixIcons.Reset,
                contentDescription = null
            )
        }
    }

    OverlayDialog(
        title = stringResource(Res.string.text_dialog_tip),
        summary = stringResource(Res.string.text_dialog_delete_fps_record_notes),
        show = showDialog,
        onDismissRequest = {
            showDialog = false
        }
    ) {
        TextButton(
            text = stringResource(Res.string.text_dialog_confirm),
            onClick = {
                FpsRecordViewModel.softDeleteSessions()
                showDialog = false
            },
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
