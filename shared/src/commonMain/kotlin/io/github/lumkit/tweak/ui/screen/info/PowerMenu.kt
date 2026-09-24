package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.rememberRequestOverlayPermission
import io.github.lumkit.tweak.overlay.OverlayMonitor
import io.github.lumkit.tweak.ui.screen.fpsRecord.showRecordOverlay
import io.github.lumkit.tweak.ui.screen.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_fps_record_overlay
import tweak_alpha.shared.generated.resources.text_fps_record_overlay_description
import tweak_alpha.shared.generated.resources.text_mini_load_overlay
import tweak_alpha.shared.generated.resources.text_mini_load_overlay_description
import tweak_alpha.shared.generated.resources.text_overlay_load
import tweak_alpha.shared.generated.resources.text_overlay_load_description
import tweak_alpha.shared.generated.resources.text_overlay_thread
import tweak_alpha.shared.generated.resources.text_overlay_thread_description
import tweak_alpha.shared.generated.resources.text_overlay_watcher
import tweak_alpha.shared.generated.resources.text_overlay_watcher_description
import tweak_alpha.shared.generated.resources.text_reboot
import tweak_alpha.shared.generated.resources.text_reboot_to_bl
import tweak_alpha.shared.generated.resources.text_reboot_to_edl
import tweak_alpha.shared.generated.resources.text_reboot_to_rec
import tweak_alpha.shared.generated.resources.text_shutdown
import tweak_alpha.shared.generated.resources.text_soft_reboot

private fun PowerAction.labelRes() = when (this) {
    PowerAction.Shutdown -> Res.string.text_shutdown
    PowerAction.Reboot -> Res.string.text_reboot
    PowerAction.SoftReboot -> Res.string.text_soft_reboot
    PowerAction.Recovery -> Res.string.text_reboot_to_rec
    PowerAction.Bootloader -> Res.string.text_reboot_to_bl
    PowerAction.Edl -> Res.string.text_reboot_to_edl
}

@Composable
fun RowScope.PowerMenu() {
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel() }
    val runtimeMode by settingsViewModel.runtimeMode.collectAsStateWithLifecycle()

    // 监听器悬浮窗
    Box {
        var popState by remember { mutableStateOf(false) }

        val requestOverlay = rememberRequestOverlayPermission {
            popState = true
        }

        IconButton(
            onClick = {
                requestOverlay()
            }
        ) {
            Icon(
                imageVector = MiuixIcons.Backup,
                contentDescription = null
            )
        }

        val loadWatcherShowState by OverlayMonitor.loadWatcherIsShowing.collectAsStateWithLifecycle()
        val threadWatcherShowState by OverlayMonitor.threadWatcherIsShowing.collectAsStateWithLifecycle()
        val miniLoadOverlayShowState by OverlayMonitor.miniLoadWatcherIsShowing.collectAsStateWithLifecycle()

        OverlayDialog(
            title = stringResource(Res.string.text_overlay_watcher),
            summary = stringResource(Res.string.text_overlay_watcher_description),
            show = popState,
            onDismissRequest = { popState = false }
        ) {
            Column {
                SwitchPreference(
                    title = stringResource(Res.string.text_overlay_load),
                    summary = stringResource(Res.string.text_overlay_load_description),
                    checked = loadWatcherShowState,
                    onCheckedChange = {
                        if (!it) {
                            OverlayMonitor.hideLoadWatcherOverlay()
                        } else {
                            OverlayMonitor.showLoadWatcherOverlay()
                        }
                    }
                )

                SwitchPreference(
                    title = stringResource(Res.string.text_overlay_thread),
                    summary = stringResource(Res.string.text_overlay_thread_description),
                    checked = threadWatcherShowState,
                    onCheckedChange = {
                        if (!it) {
                            OverlayMonitor.hideThreadWatcherOverlay()
                        } else {
                            OverlayMonitor.showThreadWatcherOverlay()
                        }
                    }
                )

                SwitchPreference(
                    title = stringResource(Res.string.text_mini_load_overlay),
                    summary = stringResource(Res.string.text_mini_load_overlay_description),
                    checked = miniLoadOverlayShowState,
                    onCheckedChange = {
                        if (!it) {
                            OverlayMonitor.hideMiniLoadWatcherOverlay()
                        } else {
                            OverlayMonitor.showMiniLoadWatcherOverlay()
                        }
                    }
                )

                ArrowPreference(
                    title = stringResource(Res.string.text_fps_record_overlay),
                    summary = stringResource(Res.string.text_fps_record_overlay_description),
                    onClick = {
                        showRecordOverlay()
                    }
                )
            }
        }
    }

    // 高级重启
    Box {
        var popState by remember { mutableStateOf(false) }

        val actions = PowerAction.entries

        IconButton(
            onClick = {
                popState = true
            }
        ) {
            Icon(
                imageVector = MiuixIcons.Close2,
                contentDescription = null
            )
        }

        OverlayListPopup(
            show = popState,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { popState = false }
        ) {
            ListPopupColumn {
                actions.forEachIndexed { index, action ->
                    DropdownImpl(
                        text = stringResource(action.labelRes()),
                        optionSize = actions.size,
                        isSelected = false,
                        index = index,
                        enabled = action.isEnabled(runtimeMode),
                        onSelectedIndexChange = {
                            scope.launch {
                                val output = ReusableShells.execSync(action.shellScript())
                                val code = parsePowerExit(output)
                                if (code != null && code != 0) {
                                    logE("power action $action failed: $code", tag = "PowerMenu")
                                }
                            }
                            popState = false
                        }
                    )
                }
            }
        }
    }
}
