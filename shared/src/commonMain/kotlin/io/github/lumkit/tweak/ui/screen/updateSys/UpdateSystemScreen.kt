package io.github.lumkit.tweak.ui.screen.updateSys

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.feature.UpdateEngineClient
import io.github.lumkit.tweak.common.feature.UpdateErrorCode
import io.github.lumkit.tweak.common.feature.UpdateStatus
import io.github.lumkit.tweak.common.feature.asMsg
import io.github.lumkit.tweak.common.utils.documentFile
import io.github.lumkit.tweak.common.utils.formatMemorySize
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import io.github.lumkit.tweak.ui.theme.colorBusy
import io.github.lumkit.tweak.ui.theme.colorSuccess
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowListPopup
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_update
import tweak_alpha.shared.generated.resources.text_alive_slot
import tweak_alpha.shared.generated.resources.text_android_version
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_feature_update_system
import tweak_alpha.shared.generated.resources.text_feature_update_system_description
import tweak_alpha.shared.generated.resources.text_func
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_install
import tweak_alpha.shared.generated.resources.text_installing_rom
import tweak_alpha.shared.generated.resources.text_select_rom
import tweak_alpha.shared.generated.resources.text_selected_rom
import tweak_alpha.shared.generated.resources.text_unzipping_rom
import tweak_alpha.shared.generated.resources.text_update_cancel
import tweak_alpha.shared.generated.resources.text_update_merge
import tweak_alpha.shared.generated.resources.text_update_reset
import tweak_alpha.shared.generated.resources.text_update_resume
import tweak_alpha.shared.generated.resources.text_update_status
import tweak_alpha.shared.generated.resources.text_update_suspend

internal val UpdateSystemProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "UpdateSystemProvider",
            title = Res.string.text_feature_update_system,
            icon = Res.drawable.ic_update,
            description = Res.string.text_feature_update_system_description,
            capabilities = setOf(
                Capability.ROOT_ONLY,
            ),
            route = Screen.UpdateSystem,
            defaultState = FeatureState.ENABLED,
            rule = {
                runCatching {
                    UpdateEngineClient.support()
                }.getOrNull() ?: false
            },
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            }
        )

    @Composable
    override fun Content() {
        UpdateSystemScreen()
    }

}

@Immutable
data class MoreOption(
    val title: StringResource,
    val onTap: () -> Unit,
    val icon: ImageVector? = null
)

@Composable
fun UpdateSystemScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backdrop = rememberLayerBackdropColor()
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current

    val status by UpdateEngineViewModel.updateStatus.collectAsStateWithLifecycle()
    val selectedRom by UpdateEngineViewModel.selectedRom.collectAsStateWithLifecycle()
    val unzipping by UpdateEngineViewModel.unzipping.collectAsStateWithLifecycle()

    var showMorePopup by remember { mutableStateOf(false) }

    val moreOptions = remember {
        listOf(
            MoreOption(
                title = Res.string.text_update_suspend,
                onTap = {
                    UpdateEngineViewModel.suspend()
                },
            ),
            MoreOption(
                title = Res.string.text_update_resume,
                onTap = {
                    UpdateEngineViewModel.resume()
                },
            ),
            MoreOption(
                title = Res.string.text_update_cancel,
                onTap = {
                    UpdateEngineViewModel.cancel()
                },
            ),
            MoreOption(
                title = Res.string.text_update_merge,
                onTap = {
                    UpdateEngineViewModel.merge()
                },
            ),
            MoreOption(
                title = Res.string.text_update_reset,
                onTap = {
                    UpdateEngineViewModel.reset()
                },
            ),
        )
    }

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.text_feature_update_system),
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
                    Box {
                        IconButton(
                            onClick = {
                                showMorePopup = true
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = null
                            )
                        }

                        WindowListPopup(
                            show = showMorePopup,
                            alignment = PopupPositionProvider.Align.End,
                            onDismissRequest = { showMorePopup = false } // 关闭弹窗菜单
                        ) {
                            ListPopupColumn {
                                moreOptions.forEachIndexed { index, option ->
                                    DropdownImpl(
                                        text = stringResource(option.title),
                                        optionSize = moreOptions.size,
                                        isSelected = false,
                                        index = index,
                                        onSelectedIndexChange = {
                                            option.onTap()
                                            showMorePopup = false
                                        },
                                        enabled = !unzipping
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) {
        var buttonHeight by remember { mutableStateOf(0.dp) }

        Box {
            LazyColumn(
                modifier = Modifier.layerBackdrop(backdrop)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical()
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = it.calculateStartPadding(direction) + 16.dp,
                    top = it.calculateTopPadding() + 16.dp,
                    end = it.calculateEndPadding(direction) + 16.dp,
                    bottom = it.calculateBottomPadding() + 32.dp + buttonHeight
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    InfoCard()
                }

                item {
                    InstallContent()
                }
            }

            Button(
                onClick = {
                    val rom = selectedRom
                    if (status is UpdateStatus.Idle && rom != null && !unzipping) {
                        UpdateEngineViewModel.installRom(rom.uri.toString())
                    }
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                enabled = status is UpdateStatus.Idle && selectedRom != null && !unzipping,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = it.calculateBottomPadding() + 16.dp)
                    .padding(start = it.calculateStartPadding(direction) + 16.dp)
                    .padding(end = it.calculateEndPadding(direction) + 16.dp)
                    .fillMaxWidth()
                    .onSizeChanged {
                        buttonHeight = with(density) { it.height.toDp() }
                    }
            ) {
                Text(text = stringResource(Res.string.text_install))
            }
        }
    }
}

@Composable
expect fun rememberAndroidVersionDetail(): State<String>

@Composable
expect fun rememberAliveSlot(): State<String>

@Composable
private fun InfoCard() {
    val androidVersionDetail by rememberAndroidVersionDetail()
    val aliveSlot by rememberAliveSlot()
    val status by UpdateEngineViewModel.updateStatus.collectAsStateWithLifecycle()
    val errorCode by UpdateEngineViewModel.updateError.collectAsStateWithLifecycle()
    var statusMsg by remember(status) { mutableStateOf(status.asMsg()) }
    val unzipping by UpdateEngineViewModel.unzipping.collectAsStateWithLifecycle()

    val statusColor by animateColorAsState(
        targetValue = when {
            // 正在更新
            status is UpdateStatus.Idle && !unzipping -> {
                MiuixTheme.colorScheme.onSurface.copy(.5f)
            }

            errorCode?.errorCode is UpdateErrorCode.Success || status is UpdateStatus.UpdatedNeedReboot -> {
                colorSuccess
            }

            errorCode?.errorCode !is UpdateErrorCode.Success && errorCode?.errorCode != null && (status !is UpdateStatus.Downloading || status !is UpdateStatus.Verifying || status !is UpdateStatus.Finalizing) -> {
                MiuixTheme.colorScheme.error
            }

            else -> colorBusy
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.background,
        )
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.text_android_version),
            summary = androidVersionDetail
        )
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.text_alive_slot),
            summary = aliveSlot
        )
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.text_update_status),
            summary = if (unzipping) {
                stringResource(Res.string.text_unzipping_rom)
            } else {
                statusMsg
            },
            summaryColor = BasicComponentDefaults.summaryColor(
                color = statusColor
            )
        )
    }
}

@Composable
private fun InstallContent() {
    val scope = rememberCoroutineScope()
    val selectedRom by UpdateEngineViewModel.selectedRom.collectAsStateWithLifecycle()
    val unzipping by UpdateEngineViewModel.unzipping.collectAsStateWithLifecycle()
    val status by UpdateEngineViewModel.updateStatus.collectAsStateWithLifecycle()

    val selectRomLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        scope.launch {
            val file = uri?.documentFile()
            if (file?.exists() == true) {
                UpdateEngineViewModel.setSelectedRom(
                    UpdateEngineViewModel.Rom(
                        name = file.name ?: "target rom.zip",
                        uri = file.uri,
                        size = file.length(),
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SmallTitle(text = stringResource(Res.string.text_func))

        AnimatedVisibility(
            visible = selectedRom == null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (status is UpdateStatus.Downloading || status is UpdateStatus.Verifying || status is UpdateStatus.UpdatedNeedReboot) {
                        stringResource(Res.string.text_installing_rom)
                    } else {
                        stringResource(Res.string.text_select_rom)
                    },
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.clip(
                        Rectangle.copy(16.dp)
                    ).clickable(
                        enabled = !unzipping && status is UpdateStatus.Idle
                    ) {
                        selectRomLauncher.launch(arrayOf("application/zip"))
                    }.padding(16.dp)
                )

            }
        }

        AnimatedVisibility(
            visible = selectedRom != null
        ) {
            selectedRom?.also {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    pressFeedbackType = PressFeedbackType.Sink,
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.primaryContainer.copy(.31f)
                    )
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(Res.string.text_selected_rom),
                        summary = remember(it) {
                            buildString {
                                append(it.name)
                                append("\n")
                                append(it.size.formatMemorySize(" "))
                            }
                        },
                        endActions = {
                            IconButton(
                                onClick = {
                                    UpdateEngineViewModel.setSelectedRom(null)
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}