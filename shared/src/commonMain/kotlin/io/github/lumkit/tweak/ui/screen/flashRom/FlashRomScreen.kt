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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import io.github.lumkit.tweak.common.utils.logD
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
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
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_line_flash_rom
import tweak_alpha.shared.generated.resources.text_line_flash_rom_description
import tweak_alpha.shared.generated.resources.text_no_device
import tweak_alpha.shared.generated.resources.text_no_target_fastboot_device
import tweak_alpha.shared.generated.resources.text_reboot
import tweak_alpha.shared.generated.resources.text_set_target_fastboot_device
import tweak_alpha.shared.generated.resources.text_start_flash
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

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.text_line_flash_rom),
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    navigationIcon = {
                        IconButton(
                            onClick = navigator::goBack
                        ) {
                            Icon(
                                MiuixIcons.Back,
                                contentDescription = stringResource(Res.string.text_go_back),
                            )
                        }
                    },
                    actions = {
                        Actions(viewModel)
                    }
                )
            },
            containerColor = MiuixTheme.colorScheme.surface,
            floatingToolbarPosition = ToolbarPosition.BottomEnd
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
                        bottom = padding.calculateBottomPadding()  + 32.dp + buttonHeight
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    onClick = {

                    },
                    enabled = true,
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
                    Text(text = stringResource(Res.string.text_start_flash))
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
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        SmallTitle(title)
        Card(
            modifier = modifier.fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.None,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer
            )
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
    PreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_devices),
    ) {

        // 选择设备
        Block {
            val devices by viewModel.fastBootDevices.collectAsStateWithLifecycle()
            val selected by viewModel.targetFastbootDevice.collectAsStateWithLifecycle()
            val items by produceState(initialValue = emptyList(), devices) {
                value = devices.map {
                    it.id
                }.toMutableList()
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
                            launch {
                                hostState.showSnackbar(getString(Res.string.text_usb_permission_required))
                            }
                            FastbootManager.requestPermission(fastbootDevice)
                        }
                        return@OverlayDropdownPreference
                    }
                    viewModel.setTargetFastbootDevice(device)
                },
                enabled = devices.isNotEmpty(),
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
            IconButton(
                onClick = {
                    expanded = true
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = null,
                )
            }

            OverlayListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.End,
                onDismissRequest = { expanded = false }
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
                        }
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
                            FastbootManager.reboot(device.fastbootDevice)
                        } finally {
                            rebootDialogState = false
                        }
                    }
                },
                onCancel = {
                    rebootDialogState = false
                },
                onDismissRequest = {
                    rebootDialogState = false
                }
            )
        }
    }
}

@Composable
private fun ChooseRomContent(viewModel: FlashRomViewModel) {
    val path by viewModel.romPath.collectAsStateWithLifecycle()

    val picker = rememberFilePickerLauncher {
        logD("result=$it", "ChooseRomContent")
        viewModel.setRomPath(it.firstOrNull() ?: "")
    }

    PreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_choose_rom_title),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = path,
            onValueChange = viewModel::setRomPath,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = stringResource(Res.string.text_choose_rom_description),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    picker.launch(FilePickerAction.File)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(color = MiuixTheme.colorScheme.primaryContainer),
            ) {
                Text(stringResource(Res.string.text_choose_rom))
            }

            Button(
                onClick = {
                    picker.launch(FilePickerAction.Folder)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(color = MiuixTheme.colorScheme.primaryContainer),
            ) {
                Text(stringResource(Res.string.text_choose_folder))
            }
        }
    }
}

@Composable
private fun FlashProgressContent(viewModel: FlashRomViewModel) {

    PreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = "",
    ) {

    }
}