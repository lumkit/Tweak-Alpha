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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.component.Block
import io.github.lumkit.tweak.common.component.Logo
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.BUILD_VERSION_CODE
import io.github.lumkit.tweak.common.utils.BUILD_VERSION_NAME
import io.github.lumkit.tweak.common.utils.hasNotificationPermission
import io.github.lumkit.tweak.common.utils.isIgnoringBatteryOptimizations
import io.github.lumkit.tweak.common.utils.jumpToAppInfo
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.openUrl
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.common.utils.requestNotificationPermission
import io.github.lumkit.tweak.common.utils.restartApp
import io.github.lumkit.tweak.common.utils.trySetIsIgnoringBatteryOptimizations
import io.github.lumkit.tweak.model.GlobalViewModel
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
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
import tweak_alpha.shared.generated.resources.text_a11y_daemon
import tweak_alpha.shared.generated.resources.text_a11y_daemon_description
import tweak_alpha.shared.generated.resources.text_about
import tweak_alpha.shared.generated.resources.text_apk_export_dir
import tweak_alpha.shared.generated.resources.text_apk_export_dir_description
import tweak_alpha.shared.generated.resources.text_app_version
import tweak_alpha.shared.generated.resources.text_auto_listen_system_update
import tweak_alpha.shared.generated.resources.text_auto_listen_system_update_description
import tweak_alpha.shared.generated.resources.text_auto_start
import tweak_alpha.shared.generated.resources.text_auto_start_description
import tweak_alpha.shared.generated.resources.text_battery_ignore_optimization_white_list
import tweak_alpha.shared.generated.resources.text_battery_ignore_optimization_white_list_description
import tweak_alpha.shared.generated.resources.text_battery_record_sample_interval
import tweak_alpha.shared.generated.resources.text_battery_record_sample_interval_description
import tweak_alpha.shared.generated.resources.text_native_daemon
import tweak_alpha.shared.generated.resources.text_native_daemon_description
import tweak_alpha.shared.generated.resources.text_native_daemon_loading
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
import tweak_alpha.shared.generated.resources.text_join_qq
import tweak_alpha.shared.generated.resources.text_jump_to_app_info
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
import tweak_alpha.shared.generated.resources.text_settings
import tweak_alpha.shared.generated.resources.text_theme
import tweak_alpha.shared.generated.resources.text_theme_dark
import tweak_alpha.shared.generated.resources.text_theme_light
import tweak_alpha.shared.generated.resources.text_theme_mode
import tweak_alpha.shared.generated.resources.text_theme_mode_description
import tweak_alpha.shared.generated.resources.text_theme_monet_dark
import tweak_alpha.shared.generated.resources.text_theme_monet_light
import tweak_alpha.shared.generated.resources.text_theme_monet_system
import tweak_alpha.shared.generated.resources.text_theme_system

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

    viewModel.LoadStateLaunchEffect {
        Watch(SettingsViewModel.NATIVE_DAEMON_TOGGLE_LOAD_ID) {
            nativeDaemonLoading = it is BaseViewModel.LoadState.Loading
        }
    }

    WindowDialog(
        show = nativeDaemonLoading,
        enableWindowDim = true,
        onDismissRequest = {},
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfiniteProgressIndicator()
            Text(
                text = stringResource(Res.string.text_native_daemon_loading),
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

        val enabledFloatNavBar by GlobalViewModel.enabledFloatNavBar.collectAsStateWithLifecycle()
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
            val tick by GlobalViewModel.infoUpdateTimeSpanMillisecondsState.collectAsStateWithLifecycle()

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
            val a11yDaemonEnabled by viewModel.a11yDaemonEnabled.collectAsStateWithLifecycle()

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
                    SwitchPreference(
                        title = stringResource(Res.string.text_a11y_daemon),
                        summary = stringResource(Res.string.text_a11y_daemon_description),
                        checked = a11yDaemonEnabled,
                        onCheckedChange = viewModel::setA11yDaemonEnabled,
                    )
                }
            }
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