package io.github.lumkit.tweak.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.Block
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.common.utils.restartApp
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_framework
import tweak_alpha.shared.generated.resources.text_framework_mode
import tweak_alpha.shared.generated.resources.text_framework_mode_description
import tweak_alpha.shared.generated.resources.text_framework_mode_root
import tweak_alpha.shared.generated.resources.text_framework_mode_shizuku
import tweak_alpha.shared.generated.resources.text_framework_mode_unknow
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
fun SettingsPage() {
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()

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
                bottom = it.calculateBottomPadding() + NavigationBarHeight + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ThemeContent()
            }

            item {
                FrameworkContent()
            }
        }
    }
}

@Composable
private fun SettingsPreferenceGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        SmallTitle(title)
        Card(
            modifier = modifier.fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Sink,
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
private fun ThemeContent() {
    val scope = rememberCoroutineScope()

    SettingsPreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_theme),
    ) {
        // 主题
        Block {
            val selected by TweakDataStore.themeModeFlow().collectAsStateWithLifecycle(ColorSchemeMode.System)
            val modes = remember {
                ColorSchemeMode.entries
            }

            OverlayDropdownPreference(
                title = stringResource(Res.string.text_theme_mode),
                summary = stringResource(Res.string.text_theme_mode_description),
                items = modes.map { stringResource(it.stringResource) },
                selectedIndex = selected.ordinal,
                onSelectedIndexChange = {
                    scope.launch {
                        TweakDataStore.setThemeMode(modes[it])
                    }
                }
            )
        }
    }
}

@Composable
private fun FrameworkContent() {
    val scope = rememberCoroutineScope()

    SettingsPreferenceGroup(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.text_framework),
    ) {
        // 运行模式
        Block {
            val selected by TweakDataStore.runtimeModeFlow().collectAsStateWithLifecycle(RuntimeMode.Unknow)
            val modes = remember { RuntimeMode.entries }

            OverlayDropdownPreference(
                title = stringResource(Res.string.text_framework_mode),
                summary = stringResource(Res.string.text_framework_mode_description),
                items = modes.map { stringResource(it.stringResource) },
                selectedIndex = selected.ordinal,
                onSelectedIndexChange = {
                    val newModel = modes[it]
                    val oldMode = selected
                    scope.launch {
                        TweakDataStore.setRuntimeMode(newModel)
                        if (newModel != oldMode) {
                            restartApp()
                        }
                    }
                }
            )
        }
    }
}
