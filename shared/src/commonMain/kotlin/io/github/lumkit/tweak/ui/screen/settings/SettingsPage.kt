package io.github.lumkit.tweak.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.Block
import io.github.lumkit.tweak.common.component.Logo
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.BUILD_VERSION_CODE
import io.github.lumkit.tweak.common.utils.BUILD_VERSION_NAME
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.hasNotificationPermission
import io.github.lumkit.tweak.common.utils.isIgnoringBatteryOptimizations
import io.github.lumkit.tweak.common.utils.jumpToAppInfo
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.openUrl
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.common.utils.requestNotificationPermission
import io.github.lumkit.tweak.common.utils.restartApp
import io.github.lumkit.tweak.common.utils.trySetIsIgnoringBatteryOptimizations
import io.github.lumkit.tweak.model.AppearanceSettingsStore
import io.github.lumkit.tweak.model.SamplingSettingsStore
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.filePicker.FilePickerAction
import io.github.lumkit.tweak.ui.screen.filePicker.rememberFilePickerLauncher
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_logo_qq
import tweak_alpha.shared.generated.resources.text_about
import tweak_alpha.shared.generated.resources.text_apk_export_dir
import tweak_alpha.shared.generated.resources.text_apk_export_dir_description
import tweak_alpha.shared.generated.resources.text_app_version
import tweak_alpha.shared.generated.resources.text_auto_listen_system_update
import tweak_alpha.shared.generated.resources.text_auto_listen_system_update_description
import tweak_alpha.shared.generated.resources.text_auto_start
import tweak_alpha.shared.generated.resources.text_auto_start_description
import tweak_alpha.shared.generated.resources.text_battery
import tweak_alpha.shared.generated.resources.text_battery_current_scale
import tweak_alpha.shared.generated.resources.text_battery_current_scale_description
import tweak_alpha.shared.generated.resources.text_battery_dual_cell
import tweak_alpha.shared.generated.resources.text_battery_dual_cell_description
import tweak_alpha.shared.generated.resources.text_battery_ignore_optimization_white_list
import tweak_alpha.shared.generated.resources.text_battery_ignore_optimization_white_list_description
import tweak_alpha.shared.generated.resources.text_battery_record_sample_interval
import tweak_alpha.shared.generated.resources.text_battery_record_sample_interval_description
import tweak_alpha.shared.generated.resources.text_battery_unit_calibration
import tweak_alpha.shared.generated.resources.text_battery_unit_calibration_description
import tweak_alpha.shared.generated.resources.text_cpu_state
import tweak_alpha.shared.generated.resources.text_crash_history
import tweak_alpha.shared.generated.resources.text_dialog_cancel
import tweak_alpha.shared.generated.resources.text_dialog_confirm
import tweak_alpha.shared.generated.resources.text_float_navigation_bar
import tweak_alpha.shared.generated.resources.text_float_navigation_bar_description
import tweak_alpha.shared.generated.resources.text_float_navigation_bar_enable_liquidity
import tweak_alpha.shared.generated.resources.text_float_navigation_bar_enable_liquidity_description
import tweak_alpha.shared.generated.resources.text_framework
import tweak_alpha.shared.generated.resources.text_framework_mode
import tweak_alpha.shared.generated.resources.text_framework_mode_description
import tweak_alpha.shared.generated.resources.text_framework_mode_root
import tweak_alpha.shared.generated.resources.text_framework_mode_shizuku
import tweak_alpha.shared.generated.resources.text_framework_mode_unknow
import tweak_alpha.shared.generated.resources.text_github
import tweak_alpha.shared.generated.resources.text_github_web
import tweak_alpha.shared.generated.resources.text_gpu_state
import tweak_alpha.shared.generated.resources.text_hide_in_background
import tweak_alpha.shared.generated.resources.text_hide_in_background_description
import tweak_alpha.shared.generated.resources.text_info_page_cards
import tweak_alpha.shared.generated.resources.text_info_page_cards_description
import tweak_alpha.shared.generated.resources.text_join_qq
import tweak_alpha.shared.generated.resources.text_jump_to_app_info
import tweak_alpha.shared.generated.resources.text_memory_state
import tweak_alpha.shared.generated.resources.text_native_daemon
import tweak_alpha.shared.generated.resources.text_native_daemon_description
import tweak_alpha.shared.generated.resources.text_native_daemon_loading
import tweak_alpha.shared.generated.resources.text_navigation_bar_enable_blur
import tweak_alpha.shared.generated.resources.text_navigation_bar_enable_blur_description
import tweak_alpha.shared.generated.resources.text_notification_permission
import tweak_alpha.shared.generated.resources.text_notification_permission_denied
import tweak_alpha.shared.generated.resources.text_open_sources
import tweak_alpha.shared.generated.resources.text_panel_refresh_tick
import tweak_alpha.shared.generated.resources.text_panel_refresh_tick_description
import tweak_alpha.shared.generated.resources.text_process_info_overview
import tweak_alpha.shared.generated.resources.text_process_info_overview_description
import tweak_alpha.shared.generated.resources.text_qq_url
import tweak_alpha.shared.generated.resources.text_runtime_mode_loading
import tweak_alpha.shared.generated.resources.text_settings
import tweak_alpha.shared.generated.resources.text_sf_latency_source
import tweak_alpha.shared.generated.resources.text_sf_latency_source_1
import tweak_alpha.shared.generated.resources.text_sf_latency_source_2
import tweak_alpha.shared.generated.resources.text_special_thanks
import tweak_alpha.shared.generated.resources.text_storage
import tweak_alpha.shared.generated.resources.text_theme
import tweak_alpha.shared.generated.resources.text_theme_dark
import tweak_alpha.shared.generated.resources.text_theme_light
import tweak_alpha.shared.generated.resources.text_theme_mode
import tweak_alpha.shared.generated.resources.text_theme_mode_description
import tweak_alpha.shared.generated.resources.text_theme_monet_dark
import tweak_alpha.shared.generated.resources.text_theme_monet_light
import tweak_alpha.shared.generated.resources.text_theme_monet_system
import tweak_alpha.shared.generated.resources.text_theme_system
import tweak_alpha.shared.generated.resources.text_ui_scale
import tweak_alpha.shared.generated.resources.text_ui_scale_description

@Preview
@Composable
private fun SettingsPagePreview() {
    SettingsPage()
}

@Composable
fun SettingsPage(
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() }
) {
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    var nativeDaemonLoading by remember { mutableStateOf(false) }
    var runtimeModeLoading by remember { mutableStateOf(false) }

    viewModel.LoadStateLaunchEffect {
        Watch(viewModel.nativeDaemonToggleSlot) {
            nativeDaemonLoading = it is BaseViewModel.LoadState.Loading
        }
        Watch(viewModel.runtimeModeToggleSlot) {
            runtimeModeLoading = it is BaseViewModel.LoadState.Loading
        }
    }

    val settingsBusy = nativeDaemonLoading || runtimeModeLoading

    WindowDialog(
        show = settingsBusy,
        enableWindowDim = true,
        // null：禁止点击遮罩/返回键交互关闭
        onDismissRequest = null,
    ) {
        val blockBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = blockBackState,
            isBackEnabled = settingsBusy,
            onBackCompleted = { },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfiniteProgressIndicator()
            Text(
                text = stringResource(
                    if (runtimeModeLoading) {
                        Res.string.text_runtime_mode_loading
                    } else {
                        Res.string.text_native_daemon_loading
                    },
                ),
                style = MiuixTheme.textStyles.main,
            )
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.text_settings),
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) {
        val padding = remember(it) {
            PaddingValues(
                start = it.calculateLeftPadding(direction),
                end = it.calculateRightPadding(direction),
            )
        }

        val enabledFloatNavBar by AppearanceSettingsStore.enabledFloatNavBar.collectAsStateWithLifecycle()
        val navBarBottomPadding by animateDpAsState(
            targetValue = if (enabledFloatNavBar) {
                28.dp
            } else {
                16.dp
            }
        )

        LazyColumn(
            modifier = Modifier.layerBackdrop(backdrop)
                .padding(padding)
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = it.calculateTopPadding() + 16.dp,
                bottom = it.calculateBottomPadding() + NavigationBarHeight + navBarBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ThemeContent(viewModel)
            }

            item {
                FrameworkContent(viewModel)
            }

            item {
                AboutContent()
            }
        }
    }
}

@Composable
private fun SettingsPreferenceGroup(
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

val ColorSchemeMode.stringResource: StringResource
    get() = when (this) {
        ColorSchemeMode.System -> Res.string.text_theme_system
        ColorSchemeMode.Light -> Res.string.text_theme_light
        ColorSchemeMode.Dark -> Res.string.text_theme_dark
        ColorSchemeMode.MonetSystem -> Res.string.text_theme_monet_system
        ColorSchemeMode.MonetLight -> Res.string.text_theme_monet_light
        ColorSchemeMode.MonetDark -> Res.string.text_theme_monet_dark
    }

val RuntimeMode.stringResource: StringResource
    get() = when (this) {
        RuntimeMode.Unknow -> Res.string.text_framework_mode_unknow
        RuntimeMode.Root -> Res.string.text_framework_mode_root
        RuntimeMode.Shizuku -> Res.string.text_framework_mode_shizuku
    }

@Composable
private fun ThemeContent(viewModel: SettingsViewModel) {
    SettingsPreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_theme),
    ) {
        // 主题
        Block {
            val selected by viewModel.themeMode.collectAsStateWithLifecycle()
            val modes = remember {
                ColorSchemeMode.entries
            }

            OverlayDropdownPreference(
                title = stringResource(Res.string.text_theme_mode),
                summary = stringResource(Res.string.text_theme_mode_description),
                items = modes.map { stringResource(it.stringResource) },
                selectedIndex = selected.ordinal,
                onSelectedIndexChange = {
                    viewModel.setThemeMode(modes[it])
                }
            )
        }

        // 全局高斯模糊
        Block {
            val enabled by viewModel.enableBlur.collectAsStateWithLifecycle()

            SwitchPreference(
                title = stringResource(Res.string.text_navigation_bar_enable_blur),
                summary = stringResource(Res.string.text_navigation_bar_enable_blur_description),
                checked = enabled,
                onCheckedChange = {
                    viewModel.setEnableBlur(it)
                }
            )
        }

        // 悬浮底部导航栏
        val enabledFloatNavBar by viewModel.enableFloatNavigationBar.collectAsStateWithLifecycle()

        Block {
            SwitchPreference(
                title = stringResource(Res.string.text_float_navigation_bar),
                summary = stringResource(Res.string.text_float_navigation_bar_description),
                checked = enabledFloatNavBar,
                onCheckedChange = {
                    viewModel.setEnableFloatNavigationBar(it)
                }
            )
        }

        AnimatedVisibility(
            visible = enabledFloatNavBar,
        ) {
            // 液态玻璃
            Block {
                val enabled by viewModel.floatNavigationBarEnableLiquidity.collectAsStateWithLifecycle()

                SwitchPreference(
                    title = stringResource(Res.string.text_float_navigation_bar_enable_liquidity),
                    summary = stringResource(Res.string.text_float_navigation_bar_enable_liquidity_description),
                    checked = enabled,
                    onCheckedChange = {
                        viewModel.setFloatNavigationBarEnableLiquidity(it)
                    }
                )
            }
        }

        // 全局缩放比
        Block {
            val uiScale by AppearanceSettingsStore.globalScaleDensity.collectAsStateWithLifecycle()
            var scale by remember(uiScale) { mutableFloatStateOf(uiScale) }
            var inputDialogState by remember { mutableStateOf(false) }

            SliderPreference(
                value = scale,
                onValueChange = {
                    scale = it
                },
                onValueChangeFinished = {
                    AppearanceSettingsStore.setGlobalScaleDensity(scale)
                },
                title = stringResource(Res.string.text_ui_scale),
                summary = stringResource(Res.string.text_ui_scale_description),
                valueRange = .8f .. 1.1f,
                showKeyPoints = true,
                keyPoints = listOf(.8f, .9f, 1.0f, 1.1f),
                endActions = {
                    Text(
                        text = buildString {
                            append("%d%%".format((scale * 100).toInt()))
                        }
                    )
                },
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                onClick = {
                    inputDialogState = true
                }
            )

            var scaleText by remember(scale) { mutableStateOf((scale * 100).toInt().toString()) }

            OverlayDialog(
                title = stringResource(Res.string.text_ui_scale),
                summary = "80% ~ 110%",
                show = inputDialogState,
                onDismissRequest = { inputDialogState = false } // 关闭对话框
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = scaleText,
                        onValueChange = {
                            scaleText = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text(
                                text = "%",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val scale = scaleText.toIntOrNull() ?: (scale * 100).toInt()
                                val safeScale = (scale / 100f).coerceIn(.8f, 1.1f)
                                AppearanceSettingsStore.setGlobalScaleDensity(safeScale)
                                inputDialogState = false
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(Res.string.text_dialog_confirm))
                        }

                        Button(
                            onClick = {
                                inputDialogState = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(Res.string.text_dialog_cancel))
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun FrameworkContent(viewModel: SettingsViewModel) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.updateNotificationPermission(it)
        viewModel.setHasRequestNotificationPermission()
    }
    val snackBarHostState = LocalSnackBarHostState.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // 更新电池优化状态
                    viewModel.updateIsIgnoringBatteryOptimizations(isIgnoringBatteryOptimizations())

                    // 更新通知权限状态
                    viewModel.updateNotificationPermission(hasNotificationPermission())

                    // 刷新 Native Daemon 存活状态
                    viewModel.refreshNativeDaemonStatus()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SettingsPreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_framework),
    ) {
        // 运行模式
        Block {
            val selected by viewModel.runtimeMode.collectAsStateWithLifecycle()
            val modes = remember { RuntimeMode.entries.filter { it != RuntimeMode.Unknow } }
            val selectedIndex = remember(selected) { modes.indexOf(selected).coerceIn(modes.indices) }

            OverlayDropdownPreference(
                title = stringResource(Res.string.text_framework_mode),
                summary = stringResource(Res.string.text_framework_mode_description),
                items = modes.map { stringResource(it.stringResource) },
                selectedIndex = selectedIndex,
                onSelectedIndexChange = {
                    val newModel = modes[it]
                    val oldMode = selected
                    viewModel.setRuntimeMode(newModel) {
                        if (newModel != oldMode) {
                            restartApp()
                        }
                    }
                }
            )
        }

        // 面板刷新间隔
        Block {
            val level by viewModel.infoUpdateTimeSpanLevel.collectAsStateWithLifecycle()
            val tick by SamplingSettingsStore.infoUpdateIntervalMs.collectAsStateWithLifecycle()

            SliderPreference(
                value = level.toFloat(),
                onValueChange = viewModel::setInfoUpdateTimeSpan,
                title = stringResource(Res.string.text_panel_refresh_tick),
                summary = stringResource(Res.string.text_panel_refresh_tick_description)
                    .format(tick),
                valueRange = 1f..10f,
                steps = 10,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step
            )
        }

        // 面板卡片显示
        Block {
            var showCardsSheet by remember { mutableStateOf(false) }

            ArrowPreference(
                title = stringResource(Res.string.text_info_page_cards),
                summary = stringResource(Res.string.text_info_page_cards_description),
                onClick = { showCardsSheet = true },
            )

            InfoPageCardsSheet(
                show = showCardsSheet,
                viewModel = viewModel,
                onDismiss = { showCardsSheet = false },
            )
        }

        // SurfaceFlinger latency 列
        Block {
            val source by viewModel.sfLatencySource.collectAsStateWithLifecycle()
            val items = listOf(
                stringResource(Res.string.text_sf_latency_source_1),
                stringResource(Res.string.text_sf_latency_source_2),
            )
            val selectedIndex = if (source == 1) 0 else 1

            OverlayDropdownPreference(
                title = stringResource(Res.string.text_sf_latency_source),
                items = items,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    viewModel.setSfLatencySource(if (index == 0) 1 else 2)
                },
            )
        }

        // 电池记录采样间隔
        Block {
            val level by viewModel.batteryRecordSampleIntervalLevel.collectAsStateWithLifecycle()
            val intervalMs = remember(level) {
                level * BatteryRecordDefaults.INTERVAL_LEVEL_RANGE_MS
            }

            SliderPreference(
                value = level.toFloat(),
                onValueChange = viewModel::setBatteryRecordSampleIntervalLevel,
                title = stringResource(Res.string.text_battery_record_sample_interval),
                summary = stringResource(Res.string.text_battery_record_sample_interval_description)
                    .format(intervalMs),
                valueRange = 1f..10f,
                steps = 10,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step
            )
        }

        // 电池单位校准
        Block {
            var showCalibrationSheet by remember { mutableStateOf(false) }

            ArrowPreference(
                title = stringResource(Res.string.text_battery_unit_calibration),
                summary = stringResource(Res.string.text_battery_unit_calibration_description),
                onClick = { showCalibrationSheet = true },
            )

            BatteryUnitCalibrationSheet(
                show = showCalibrationSheet,
                viewModel = viewModel,
                onDismiss = { showCalibrationSheet = false },
            )
        }

        // 进程信息概览
        Block {
            val enabled by viewModel.enableProcessInfoOverview.collectAsStateWithLifecycle()

            SwitchPreference(
                title = stringResource(Res.string.text_process_info_overview),
                summary = stringResource(Res.string.text_process_info_overview_description),
                checked = enabled,
                onCheckedChange = viewModel::setEnableProcessInfoOverview,
            )
        }

        // 自启动
        Block {
            val autoStart by viewModel.autoStartApp.collectAsStateWithLifecycle()
            val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
            val nativeDaemonRunning by viewModel.nativeDaemonRunning.collectAsStateWithLifecycle()
            SwitchPreference(
                title = stringResource(Res.string.text_auto_start),
                summary = stringResource(Res.string.text_auto_start_description),
                checked = autoStart,
                onCheckedChange = viewModel::setAutoStartApp,
            )

            AnimatedVisibility(
                visible = autoStart
            ) {
                Column {
                    SwitchPreference(
                        title = stringResource(Res.string.text_battery_ignore_optimization_white_list),
                        summary = stringResource(Res.string.text_battery_ignore_optimization_white_list_description),
                        checked = isIgnoringBatteryOptimizations,
                        onCheckedChange = {
                            if (!isIgnoringBatteryOptimizations) {
                                trySetIsIgnoringBatteryOptimizations()
                            }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(Res.string.text_native_daemon),
                        summary = stringResource(Res.string.text_native_daemon_description),
                        checked = nativeDaemonRunning,
                        onCheckedChange = viewModel::setNativeDaemonEnabled,
                    )
                }
            }
        }

        // 后台隐藏
        Block {
            val hideInBackground by AppearanceSettingsStore.hideInBackground.collectAsStateWithLifecycle()

            SwitchPreference(
                title = stringResource(Res.string.text_hide_in_background),
                summary = stringResource(Res.string.text_hide_in_background_description),
                checked = hideInBackground,
                onCheckedChange = AppearanceSettingsStore::setHideInBackground,
            )
        }

        // 通知权限
        Block {
            val hasNotificationPermission by viewModel.notificationPermission.collectAsStateWithLifecycle()
            val hasRequest by viewModel.hasRequestNotificationPermission.collectAsStateWithLifecycle()
            val autoListen by viewModel.autoListenSystemUpdate.collectAsStateWithLifecycle()

            SwitchPreference(
                title = stringResource(Res.string.text_notification_permission),
                checked = hasNotificationPermission,
                onCheckedChange = {
                    if (hasRequest && !hasNotificationPermission) {
                        scope.launch {
                            val result = snackBarHostState.showSnackbar(
                                message = getString(Res.string.text_notification_permission_denied),
                                actionLabel = getString(Res.string.text_jump_to_app_info)
                            )
                            logD("result: $result")
                            when (result) {
                                SnackbarResult.Dismissed -> Unit
                                SnackbarResult.ActionPerformed -> {
                                    jumpToAppInfo()
                                }
                            }
                        }
                    }
                    if (!hasNotificationPermission()) {
                        notificationLauncher.requestNotificationPermission()
                    }
                }
            )

            AnimatedVisibility(visible = hasNotificationPermission) {
                SwitchPreference(
                    title = stringResource(Res.string.text_auto_listen_system_update),
                    summary = stringResource(Res.string.text_auto_listen_system_update_description),
                    checked = autoListen,
                    onCheckedChange = viewModel::setAutoListenSystemUpdate,
                )
            }
        }

        // 安装包提取目录
        Block {
            val exportDir by viewModel.apkExportDir.collectAsStateWithLifecycle()
            val folderPicker = rememberFilePickerLauncher { paths ->
                val path = paths.firstOrNull()?.takeIf(String::isNotBlank) ?: return@rememberFilePickerLauncher
                viewModel.setApkExportDir(path)
            }

            ArrowPreference(
                title = stringResource(Res.string.text_apk_export_dir),
                summary = exportDir.ifBlank {
                    stringResource(Res.string.text_apk_export_dir_description)
                },
                onClick = {
                    folderPicker.launch(FilePickerAction.Folder)
                },
            )
        }
    }
}

@Composable
private fun AboutContent() {
    val navigator = LocalNavigator.current

    SettingsPreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_about),
    ) {
        // 版本信息
        val summary = remember { "v${BUILD_VERSION_NAME}($BUILD_VERSION_CODE)" }
        Block {
            ArrowPreference(
                title = stringResource(Res.string.text_app_version),
                summary = summary,
                startAction = {
                    Logo(modifier = Modifier.size(28.dp))
                },
                onClick = {

                }
            )
        }

        // 历史崩溃
        Block {
            ArrowPreference(
                title = stringResource(Res.string.text_crash_history),
                onClick = {
                    navigator.navigate(Screen.CrashHistory)
                }
            )
        }

        // 开源地址
        Block {
            val url = stringResource(Res.string.text_github_web)
            ArrowPreference(
                title = stringResource(Res.string.text_github),
                summary = url,
                onClick = {
                    openUrl(url)
                }
            )
        }

        // 开源许可
        Block {
            ArrowPreference(
                title = stringResource(Res.string.text_open_sources),
                onClick = {
                    navigator.navigate(Screen.OpenSources)
                }
            )
        }

        // 特别鸣谢
        Block {
            ArrowPreference(
                title = stringResource(Res.string.text_special_thanks),
                onClick = {
                    navigator.navigate(Screen.SpecialThanks)
                }
            )
        }

        // 加QQ群
        Block {
            val qqUrl = stringResource(Res.string.text_qq_url)
            ArrowPreference(
                title = stringResource(Res.string.text_join_qq),
                startAction = {
                    Image(
                        painter = painterResource(Res.drawable.ic_logo_qq),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = {
                    openUrl(qqUrl)
                }
            )
        }
    }
}

@Composable
private fun InfoPageCardsSheet(
    show: Boolean,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val enabledCards by viewModel.infoPageEnabledCards.collectAsStateWithLifecycle()

    OverlayBottomSheet(
        show = show,
        title = stringResource(Res.string.text_info_page_cards),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                pressFeedbackType = PressFeedbackType.None,
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                ),
            ) {
                InfoPageCardSwitch(
                    title = stringResource(Res.string.text_cpu_state),
                    key = "cpu",
                    enabledCards = enabledCards,
                    onCheckedChange = viewModel::setInfoPageCardEnabled,
                )
                InfoPageCardSwitch(
                    title = stringResource(Res.string.text_memory_state),
                    key = "memory",
                    enabledCards = enabledCards,
                    onCheckedChange = viewModel::setInfoPageCardEnabled,
                )
                InfoPageCardSwitch(
                    title = stringResource(Res.string.text_gpu_state),
                    key = "gpu",
                    enabledCards = enabledCards,
                    onCheckedChange = viewModel::setInfoPageCardEnabled,
                )
                InfoPageCardSwitch(
                    title = stringResource(Res.string.text_battery),
                    key = "battery",
                    enabledCards = enabledCards,
                    onCheckedChange = viewModel::setInfoPageCardEnabled,
                )
                InfoPageCardSwitch(
                    title = stringResource(Res.string.text_storage),
                    key = "storage",
                    enabledCards = enabledCards,
                    onCheckedChange = viewModel::setInfoPageCardEnabled,
                )
            }
        }
    }
}

@Composable
private fun InfoPageCardSwitch(
    title: String,
    key: String,
    enabledCards: Set<String>,
    onCheckedChange: (String, Boolean) -> Unit,
) {
    val isChecked = key in enabledCards
    val isLastEnabled = isChecked && enabledCards.size == 1
    SwitchPreference(
        title = title,
        checked = isChecked,
        enabled = !isLastEnabled,
        onCheckedChange = { checked ->
            onCheckedChange(key, checked)
        },
    )
}

@Composable
private fun BatteryUnitCalibrationSheet(
    show: Boolean,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val dualCell by viewModel.batteryDualCell.collectAsStateWithLifecycle()
    val scale by viewModel.batteryCurrentScale.collectAsStateWithLifecycle()
    val scaleIndex = remember(scale) {
        BatteryReadingNormalize.scaleIndex(scale).toFloat()
    }
    val maxIndex = (BatteryReadingNormalize.SCALE_STEPS.lastIndex).toFloat()

    OverlayBottomSheet(
        show = show,
        title = stringResource(Res.string.text_battery_unit_calibration),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                pressFeedbackType = PressFeedbackType.None,
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                ),
            ) {
                SwitchPreference(
                    title = stringResource(Res.string.text_battery_dual_cell),
                    summary = stringResource(Res.string.text_battery_dual_cell_description),
                    checked = dualCell,
                    onCheckedChange = viewModel::setBatteryDualCell,
                )
                SliderPreference(
                    value = scaleIndex,
                    onValueChange = viewModel::setBatteryCurrentScaleIndex,
                    title = stringResource(Res.string.text_battery_current_scale),
                    summary = stringResource(Res.string.text_battery_current_scale_description)
                        .format(scale.toString()),
                    valueRange = 0f..maxIndex,
                    steps = (BatteryReadingNormalize.SCALE_STEPS.size - 2).coerceAtLeast(0),
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                )
            }
        }
    }
}
