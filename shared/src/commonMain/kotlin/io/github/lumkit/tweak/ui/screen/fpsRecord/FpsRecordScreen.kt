package io.github.lumkit.tweak.ui.screen.fpsRecord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.database.fps.table.FpsNoteSessionEntity
import io.github.lumkit.tweak.common.utils.AppsHelper
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_platform_device_style
import tweak_alpha.shared.generated.resources.ic_record_chart
import tweak_alpha.shared.generated.resources.ic_soc
import tweak_alpha.shared.generated.resources.ic_system_version
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
import tweak_alpha.shared.generated.resources.text_fps_recording
import tweak_alpha.shared.generated.resources.text_fps_recording_description
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_platform_model
import tweak_alpha.shared.generated.resources.text_platform_type
import tweak_alpha.shared.generated.resources.text_platform_version
import tweak_alpha.shared.generated.resources.text_record_table
import tweak_alpha.shared.generated.resources.text_record_table_description

internal val FpsRecordingProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "FpsRecordingProvider",
            title = Res.string.text_fps_recording,
            icon = Res.drawable.ic_record_chart,
            description = Res.string.text_fps_recording_description,
            capabilities = setOf(
                Capability.SHIZUKU_OR_ROOT,
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
private fun FpsRecordContent(
    viewModel: FpsRecordViewModel = viewModel { FpsRecordViewModel() }
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backdrop = rememberLayerBackdropColor()
    val direction = LocalLayoutDirection.current
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    var floatActionBarHeight by remember { mutableStateOf(0.dp) }

    // 悬浮窗权限申请：有权限直接显示，无权限跳转设置页，返回后自动检查
    val requestOverlay = rememberRequestOverlayPermission {
        showRecordOverlay()
    }

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.text_fps_recording),
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
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
        floatingToolbar = {
            FloatingToolbar(
                modifier = Modifier.onSizeChanged {
                    floatActionBarHeight = with(density) {
                        it.height.toDp()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            requestOverlay()
                        }
                    ) {
                        Icon(MiuixIcons.Add, contentDescription = null)
                    }
                    IconButton(
                        onClick = {

                        }
                    ) {
                        Icon(MiuixIcons.SelectAll, contentDescription = null)
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomEnd
    ) {
        val apps by AppsHelper.apps.collectAsStateWithLifecycle()

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
                    RecordHead(sessions)
                }

                items(sessions) { session ->
                    RecordItem(session)
                }


                items(apps) {
                    Card {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = it.iconPath,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )

                            Column {
                                Text(
                                    text = it.appName,
                                    style = MiuixTheme.textStyles.body1,
                                )
                                Text(
                                    text = it.packageName,
                                    style = MiuixTheme.textStyles.footnote2,
                                )
                            }
                        }
                    }
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
    sessions: List<FpsNoteSessionEntity>,
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
private fun RecordItem(session: FpsNoteSessionEntity) {

}