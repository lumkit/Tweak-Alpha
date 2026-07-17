package io.github.lumkit.tweak.ui.screen.flashRom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.component.AlertDialog
import io.github.lumkit.tweak.common.component.Block
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.feature.FastbootManager
import io.github.lumkit.tweak.common.feature.LineFlashBehavior
import io.github.lumkit.tweak.common.feature.LineFlashScript
import io.github.lumkit.tweak.common.feature.commitStartLineFlash
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import io.github.lumkit.tweak.ui.screen.filePicker.FilePickerAction
import io.github.lumkit.tweak.ui.screen.filePicker.rememberFilePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_app_flash
import tweak_alpha.shared.generated.resources.text_choose_folder
import tweak_alpha.shared.generated.resources.text_choose_rom
import tweak_alpha.shared.generated.resources.text_choose_rom_description
import tweak_alpha.shared.generated.resources.text_choose_rom_title
import tweak_alpha.shared.generated.resources.text_connected_fastboot_device
import tweak_alpha.shared.generated.resources.text_devices
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_flash_choose_script
import tweak_alpha.shared.generated.resources.text_flash_choose_script_summary
import tweak_alpha.shared.generated.resources.text_flash_error_item
import tweak_alpha.shared.generated.resources.text_flash_phase_error
import tweak_alpha.shared.generated.resources.text_flash_phase_flashing
import tweak_alpha.shared.generated.resources.text_flash_phase_ready
import tweak_alpha.shared.generated.resources.text_flash_phase_success
import tweak_alpha.shared.generated.resources.text_flash_phase_unpacking
import tweak_alpha.shared.generated.resources.text_flash_phase_validating
import tweak_alpha.shared.generated.resources.text_flash_progress_format
import tweak_alpha.shared.generated.resources.text_flash_ready_hint
import tweak_alpha.shared.generated.resources.text_flash_reboot_fastboot_action
import tweak_alpha.shared.generated.resources.text_flash_reboot_fastboot_failed
import tweak_alpha.shared.generated.resources.text_flash_reboot_fastboot_hint
import tweak_alpha.shared.generated.resources.text_flash_reboot_fastboot_success
import tweak_alpha.shared.generated.resources.text_flash_script_clean_all
import tweak_alpha.shared.generated.resources.text_flash_script_clean_all_lock
import tweak_alpha.shared.generated.resources.text_flash_script_clean_all_lock_crc
import tweak_alpha.shared.generated.resources.text_flash_script_custom
import tweak_alpha.shared.generated.resources.text_flash_script_save_user_data
import tweak_alpha.shared.generated.resources.text_flash_task_check_done
import tweak_alpha.shared.generated.resources.text_flash_task_check_failed
import tweak_alpha.shared.generated.resources.text_flash_task_check_pending
import tweak_alpha.shared.generated.resources.text_flash_task_check_running
import tweak_alpha.shared.generated.resources.text_flash_task_command_done
import tweak_alpha.shared.generated.resources.text_flash_task_command_failed
import tweak_alpha.shared.generated.resources.text_flash_task_command_pending
import tweak_alpha.shared.generated.resources.text_flash_task_command_running
import tweak_alpha.shared.generated.resources.text_flash_task_completed
import tweak_alpha.shared.generated.resources.text_flash_task_erase_done
import tweak_alpha.shared.generated.resources.text_flash_task_erase_failed
import tweak_alpha.shared.generated.resources.text_flash_task_erase_pending
import tweak_alpha.shared.generated.resources.text_flash_task_erase_running
import tweak_alpha.shared.generated.resources.text_flash_task_failed_summary
import tweak_alpha.shared.generated.resources.text_flash_task_flash_done
import tweak_alpha.shared.generated.resources.text_flash_task_flash_failed
import tweak_alpha.shared.generated.resources.text_flash_task_flash_pending
import tweak_alpha.shared.generated.resources.text_flash_task_flash_running
import tweak_alpha.shared.generated.resources.text_flash_task_in_progress
import tweak_alpha.shared.generated.resources.text_flash_task_lock_done
import tweak_alpha.shared.generated.resources.text_flash_task_lock_failed
import tweak_alpha.shared.generated.resources.text_flash_task_lock_pending
import tweak_alpha.shared.generated.resources.text_flash_task_lock_running
import tweak_alpha.shared.generated.resources.text_flash_task_percent
import tweak_alpha.shared.generated.resources.text_flash_task_reboot_done
import tweak_alpha.shared.generated.resources.text_flash_task_reboot_failed
import tweak_alpha.shared.generated.resources.text_flash_task_reboot_pending
import tweak_alpha.shared.generated.resources.text_flash_task_reboot_running
import tweak_alpha.shared.generated.resources.text_flash_task_slot_done
import tweak_alpha.shared.generated.resources.text_flash_task_slot_failed
import tweak_alpha.shared.generated.resources.text_flash_task_slot_pending
import tweak_alpha.shared.generated.resources.text_flash_task_slot_running
import tweak_alpha.shared.generated.resources.text_flash_task_waiting
import tweak_alpha.shared.generated.resources.text_flash_verify_and_flash
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_line_flash_rom
import tweak_alpha.shared.generated.resources.text_line_flash_rom_description
import tweak_alpha.shared.generated.resources.text_no_device
import tweak_alpha.shared.generated.resources.text_no_target_fastboot_device
import tweak_alpha.shared.generated.resources.text_reboot
import tweak_alpha.shared.generated.resources.text_set_target_fastboot_device
import tweak_alpha.shared.generated.resources.text_usb_permission_required
import tweak_alpha.shared.generated.resources.text_usb_reboot_fastboot
import tweak_alpha.shared.generated.resources.text_usb_reboot_fastboot_confirm

internal val FlashRomProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "FlashRomProvider",
            title = Res.string.text_line_flash_rom,
            icon = Res.drawable.ic_app_flash,
            description = Res.string.text_line_flash_rom_description,
            capabilities = setOf(
                Capability.SHIZUKU_OR_ROOT,
            ),
            route = Screen.FlashRom,
            defaultState = FeatureState.ENABLED,
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            }
        )

    @Composable
    override fun Content() {
        FlashRomScreen()
    }
}

@Composable
private fun FlashRomScreen(
    viewModel: FlashRomViewModel = viewModel { FlashRomViewModel }
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backdrop = rememberLayerBackdropColor()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current

    val scope = rememberCoroutineScope { Dispatchers.IO }
    val hostState = LocalSnackBarHostState.current
    var buttonHeight by remember { mutableStateOf(0.dp) }
    val startEnabled by viewModel.startEnabled.collectAsStateWithLifecycle()

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.text_line_flash_rom),
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
                    actions = { Actions(viewModel) },
                )
            },
            containerColor = MiuixTheme.colorScheme.surface,
            floatingToolbarPosition = ToolbarPosition.BottomEnd,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.layerBackdrop(backdrop)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .overScrollVertical()
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(direction) + 16.dp,
                        top = padding.calculateTopPadding() + 16.dp,
                        end = padding.calculateEndPadding(direction) + 16.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp + buttonHeight,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ConnectionDevicesContent(viewModel, scope, hostState)
                    }
                    item {
                        ChooseRomContent(viewModel)
                    }
                    item {
                        FlashProgressContent(viewModel)
                    }
                }

                Button(
                    onClick = { commitStartLineFlash() },
                    enabled = startEnabled,
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
                    Text(text = stringResource(Res.string.text_flash_verify_and_flash))
                }
            }
        }
    }
}

@Composable
private fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotBlank()) {
            SmallTitle(title)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.None,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun ConnectionDevicesContent(
    viewModel: FlashRomViewModel,
    scope: CoroutineScope,
    hostState: SnackbarHostState,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_devices),
    ) {
        Block {
            val devices by viewModel.fastBootDevices.collectAsStateWithLifecycle()
            val selected by viewModel.targetFastbootDevice.collectAsStateWithLifecycle()
            val items by produceState(initialValue = emptyList(), devices) {
                value = devices.map { it.id }.toMutableList()
            }
            val selectedIndex by produceState(-1, devices, selected) {
                value = devices.indexOf(selected)
            }

            OverlayDropdownPreference(
                title = stringResource(Res.string.text_set_target_fastboot_device),
                summary = if (selected == null) {
                    stringResource(Res.string.text_no_target_fastboot_device)
                } else {
                    stringResource(Res.string.text_connected_fastboot_device).format(
                        selected?.id ?: ""
                    )
                },
                items = items,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = {
                    val device = devices.getOrNull(it)
                    if (device == null) {
                        scope.launch {
                            hostState.showSnackbar(getString(Res.string.text_no_device))
                        }
                        return@OverlayDropdownPreference
                    }
                    val fastbootDevice = device.fastbootDevice
                    if (!fastbootDevice.hasPermission) {
                        scope.launch {
                            runCatching {
                                launch {
                                    hostState.showSnackbar(getString(Res.string.text_usb_permission_required))
                                }
                                FastbootManager.requestPermission(fastbootDevice)
                            }.onFailure {
                                hostState.showSnackbar(it.message ?: it.toString())
                            }
                        }
                        return@OverlayDropdownPreference
                    }
                    viewModel.setTargetFastbootDevice(device)
                },
                enabled = devices.isNotEmpty() && ui.deviceSelectionEnabled,
            )
        }
    }
}

@Composable
private fun Actions(viewModel: FlashRomViewModel) {
    val selectedDevice by viewModel.targetFastbootDevice.collectAsStateWithLifecycle()
    var rebootDialogState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    selectedDevice?.also { device ->
        var expanded by remember { mutableStateOf(false) }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = null,
                )
            }

            OverlayListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.End,
                onDismissRequest = { expanded = false },
            ) {
                ListPopupColumn {
                    DropdownImpl(
                        text = stringResource(Res.string.text_usb_reboot_fastboot).format(device.id),
                        optionSize = 1,
                        isSelected = false,
                        index = 0,
                        onSelectedIndexChange = {
                            rebootDialogState = true
                            expanded = false
                        },
                    )
                }
            }
        }

        Block {
            AlertDialog(
                show = rebootDialogState,
                summary = stringResource(Res.string.text_usb_reboot_fastboot_confirm).format(device.id),
                confirmText = stringResource(Res.string.text_reboot),
                onConfirm = {
                    scope.launch {
                        try {
                            runCatching {
                                FastbootManager.reboot(device.fastbootDevice)
                            }.onFailure {
                                // 重启失败不闪退，仅关闭对话框；错误可由后续设备列表刷新体现
                            }
                        } finally {
                            rebootDialogState = false
                        }
                    }
                },
                onCancel = { rebootDialogState = false },
                onDismissRequest = { rebootDialogState = false },
            )
        }
    }
}

@Composable
private fun ChooseRomContent(viewModel: FlashRomViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberFilePickerLauncher {
        viewModel.setRomPath(it.firstOrNull() ?: "")
    }

    PreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_choose_rom_title),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = ui.romPath,
            onValueChange = viewModel::setRomPath,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = stringResource(Res.string.text_choose_rom_description),
            singleLine = true,
            enabled = !ui.romSelectionLocked && !ui.busy,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = if (ui.scripts.isEmpty()) 16.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { picker.launch(FilePickerAction.File) },
                modifier = Modifier.weight(1f),
                enabled = !ui.romSelectionLocked && !ui.busy,
                colors = ButtonDefaults.buttonColorsPrimary(color = MiuixTheme.colorScheme.primaryContainer),
            ) {
                Text(stringResource(Res.string.text_choose_rom))
            }

            Button(
                onClick = { picker.launch(FilePickerAction.Folder) },
                modifier = Modifier.weight(1f),
                enabled = !ui.romSelectionLocked && !ui.busy,
                colors = ButtonDefaults.buttonColorsPrimary(color = MiuixTheme.colorScheme.primaryContainer),
            ) {
                Text(stringResource(Res.string.text_choose_folder))
            }
        }

        if (ui.scripts.isNotEmpty()) {
            val scriptLabels = ui.scripts.map { it.displayLabel() }
            val selectedIndex = ui.scripts
                .indexOfFirst { it.name == ui.selectedScriptName }
                .coerceAtLeast(0)
            val selectedLabel = ui.scripts.getOrNull(selectedIndex)?.displayLabel()
            Block {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.text_flash_choose_script),
                    summary = selectedLabel
                        ?: stringResource(Res.string.text_flash_choose_script_summary),
                    items = scriptLabels,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { index ->
                        ui.scripts.getOrNull(index)?.name?.let(viewModel::setSelectedScript)
                    },
                    enabled = !ui.busy && ui.phase != FlashRomViewModel.Phase.Flashing,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LineFlashScript.displayLabel(): String {
    return when (behavior) {
        LineFlashBehavior.CLEAN_ALL -> stringResource(Res.string.text_flash_script_clean_all)
        LineFlashBehavior.CLEAN_ALL_AND_LOCK -> {
            if (name.contains("crc", ignoreCase = true)) {
                stringResource(Res.string.text_flash_script_clean_all_lock_crc)
            } else {
                stringResource(Res.string.text_flash_script_clean_all_lock)
            }
        }
        LineFlashBehavior.SAVE_USER_DATA -> stringResource(Res.string.text_flash_script_save_user_data)
        LineFlashBehavior.CUSTOM -> stringResource(
            Res.string.text_flash_script_custom,
            name.removeSuffix(".bat").removeSuffix(".BAT"),
        )
    }
}

@Composable
private fun FlashProgressContent(viewModel: FlashRomViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    if (ui.phase == FlashRomViewModel.Phase.Idle && ui.errorMessage.isNullOrBlank()) {
        return
    }

    val title = when (ui.phase) {
        FlashRomViewModel.Phase.Unpacking -> stringResource(Res.string.text_flash_phase_unpacking)
        FlashRomViewModel.Phase.Validating -> stringResource(Res.string.text_flash_phase_validating)
        FlashRomViewModel.Phase.Ready -> stringResource(Res.string.text_flash_phase_ready)
        FlashRomViewModel.Phase.Flashing -> stringResource(Res.string.text_flash_phase_flashing)
        FlashRomViewModel.Phase.Success -> stringResource(Res.string.text_flash_phase_success)
        FlashRomViewModel.Phase.Error -> stringResource(Res.string.text_flash_phase_error)
        FlashRomViewModel.Phase.Idle -> ""
    }

    PreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = title,
    ) {
        when (ui.phase) {
            FlashRomViewModel.Phase.Unpacking, FlashRomViewModel.Phase.Validating -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinearProgressIndicator(
                        progress = ui.progress.percent / 100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            Res.string.text_flash_progress_format,
                            ui.progress.percent,
                            ui.progress.message,
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }

            FlashRomViewModel.Phase.Ready -> {
                Text(
                    text = stringResource(Res.string.text_flash_ready_hint),
                    modifier = Modifier.padding(16.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }

            FlashRomViewModel.Phase.Flashing, FlashRomViewModel.Phase.Success, FlashRomViewModel.Phase.Error -> {
                val hostState = LocalSnackBarHostState.current
                val scope = rememberCoroutineScope()
                val selectedDevice by viewModel.targetFastbootDevice.collectAsStateWithLifecycle()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(ui.flashTasks, key = { it.id }) { task ->
                        FlashTaskRow(task)
                    }
                    if (!ui.errorMessage.isNullOrBlank()) {
                        item {
                            BasicComponent(
                                title = stringResource(Res.string.text_flash_phase_error),
                                summary = ui.errorMessage,
                            )
                        }
                    }
                    if (ui.phase == FlashRomViewModel.Phase.Error && ui.suggestRebootFastboot) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.text_flash_reboot_fastboot_hint),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                                Button(
                                    onClick = {
                                        val device = selectedDevice?.fastbootDevice
                                        if (device == null) {
                                            scope.launch {
                                                hostState.showSnackbar(
                                                    getString(Res.string.text_no_target_fastboot_device),
                                                )
                                            }
                                            return@Button
                                        }
                                        scope.launch {
                                            runCatching {
                                                FastbootManager.rebootBootloader(device)
                                            }.onSuccess {
                                                hostState.showSnackbar(
                                                    getString(Res.string.text_flash_reboot_fastboot_success),
                                                )
                                            }.onFailure {
                                                hostState.showSnackbar(
                                                    getString(
                                                        Res.string.text_flash_reboot_fastboot_failed,
                                                        it.message ?: it.toString(),
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    enabled = selectedDevice != null && !ui.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text(stringResource(Res.string.text_flash_reboot_fastboot_action))
                                }
                            }
                        }
                    }
                }
            }

            FlashRomViewModel.Phase.Idle -> {
                if (!ui.errorMessage.isNullOrBlank()) {
                    BasicComponent(
                        title = stringResource(
                            Res.string.text_flash_error_item,
                            ui.errorMessage.orEmpty(),
                        ),
                        summary = ui.errorMessage,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashTaskRow(task: FlashRomViewModel.FlashTaskUi) {
    val title = flashTaskTitle(task)
    val summary = when (task.status) {
        FlashRomViewModel.FlashTaskStatus.Running -> {
            if (task.kind == FlashRomViewModel.FlashTaskKind.Flash && task.percent > 0) {
                stringResource(Res.string.text_flash_task_percent, task.percent)
            } else {
                stringResource(Res.string.text_flash_task_in_progress)
            }
        }

        FlashRomViewModel.FlashTaskStatus.Done -> {
            stringResource(Res.string.text_flash_task_completed)
        }

        FlashRomViewModel.FlashTaskStatus.Pending -> {
            stringResource(Res.string.text_flash_task_waiting)
        }

        FlashRomViewModel.FlashTaskStatus.Failed -> {
            stringResource(Res.string.text_flash_task_failed_summary)
        }
    }

    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            when (task.status) {
                FlashRomViewModel.FlashTaskStatus.Running -> {
                    InfiniteProgressIndicator(modifier = Modifier.size(22.dp))
                }

                FlashRomViewModel.FlashTaskStatus.Done -> {
                    Checkbox(
                        state = ToggleableState.On,
                        onClick = null,
                    )
                }

                FlashRomViewModel.FlashTaskStatus.Pending, FlashRomViewModel.FlashTaskStatus.Failed -> {
                    Checkbox(
                        state = ToggleableState.Off,
                        onClick = null,
                    )
                }
            }
        },
    )
}

@Composable
private fun flashTaskTitle(task: FlashRomViewModel.FlashTaskUi): String {
    val label = task.label
    return when (task.kind) {
        FlashRomViewModel.FlashTaskKind.Flash -> when (task.status) {
            FlashRomViewModel.FlashTaskStatus.Running ->
                stringResource(Res.string.text_flash_task_flash_running, label)
            FlashRomViewModel.FlashTaskStatus.Done ->
                stringResource(Res.string.text_flash_task_flash_done, label)
            FlashRomViewModel.FlashTaskStatus.Pending ->
                stringResource(Res.string.text_flash_task_flash_pending, label)
            FlashRomViewModel.FlashTaskStatus.Failed ->
                stringResource(Res.string.text_flash_task_flash_failed, label)
        }

        FlashRomViewModel.FlashTaskKind.Erase -> when (task.status) {
            FlashRomViewModel.FlashTaskStatus.Running ->
                stringResource(Res.string.text_flash_task_erase_running, label)
            FlashRomViewModel.FlashTaskStatus.Done ->
                stringResource(Res.string.text_flash_task_erase_done, label)
            FlashRomViewModel.FlashTaskStatus.Pending ->
                stringResource(Res.string.text_flash_task_erase_pending, label)
            FlashRomViewModel.FlashTaskStatus.Failed ->
                stringResource(Res.string.text_flash_task_erase_failed, label)
        }

        FlashRomViewModel.FlashTaskKind.Check -> when (task.status) {
            FlashRomViewModel.FlashTaskStatus.Running ->
                stringResource(Res.string.text_flash_task_check_running, label)
            FlashRomViewModel.FlashTaskStatus.Done ->
                stringResource(Res.string.text_flash_task_check_done, label)
            FlashRomViewModel.FlashTaskStatus.Pending ->
                stringResource(Res.string.text_flash_task_check_pending, label)
            FlashRomViewModel.FlashTaskStatus.Failed ->
                stringResource(Res.string.text_flash_task_check_failed, label)
        }

        FlashRomViewModel.FlashTaskKind.Reboot -> when (task.status) {
            FlashRomViewModel.FlashTaskStatus.Running ->
                stringResource(Res.string.text_flash_task_reboot_running)
            FlashRomViewModel.FlashTaskStatus.Done ->
                stringResource(Res.string.text_flash_task_reboot_done)
            FlashRomViewModel.FlashTaskStatus.Pending ->
                stringResource(Res.string.text_flash_task_reboot_pending)
            FlashRomViewModel.FlashTaskStatus.Failed ->
                stringResource(Res.string.text_flash_task_reboot_failed)
        }

        FlashRomViewModel.FlashTaskKind.OemLock -> when (task.status) {
            FlashRomViewModel.FlashTaskStatus.Running ->
                stringResource(Res.string.text_flash_task_lock_running)
            FlashRomViewModel.FlashTaskStatus.Done ->
                stringResource(Res.string.text_flash_task_lock_done)
            FlashRomViewModel.FlashTaskStatus.Pending ->
                stringResource(Res.string.text_flash_task_lock_pending)
            FlashRomViewModel.FlashTaskStatus.Failed ->
                stringResource(Res.string.text_flash_task_lock_failed)
        }

        FlashRomViewModel.FlashTaskKind.SetActive -> when (task.status) {
            FlashRomViewModel.FlashTaskStatus.Running ->
                stringResource(Res.string.text_flash_task_slot_running, label)
            FlashRomViewModel.FlashTaskStatus.Done ->
                stringResource(Res.string.text_flash_task_slot_done, label)
            FlashRomViewModel.FlashTaskStatus.Pending ->
                stringResource(Res.string.text_flash_task_slot_pending, label)
            FlashRomViewModel.FlashTaskStatus.Failed ->
                stringResource(Res.string.text_flash_task_slot_failed, label)
        }

        FlashRomViewModel.FlashTaskKind.Command -> when (task.status) {
            FlashRomViewModel.FlashTaskStatus.Running ->
                stringResource(Res.string.text_flash_task_command_running, label)
            FlashRomViewModel.FlashTaskStatus.Done ->
                stringResource(Res.string.text_flash_task_command_done, label)
            FlashRomViewModel.FlashTaskStatus.Pending ->
                stringResource(Res.string.text_flash_task_command_pending, label)
            FlashRomViewModel.FlashTaskStatus.Failed ->
                stringResource(Res.string.text_flash_task_command_failed, label)
        }
    }
}
