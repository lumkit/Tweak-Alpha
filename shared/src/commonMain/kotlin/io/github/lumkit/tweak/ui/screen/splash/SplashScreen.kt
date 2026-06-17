package io.github.lumkit.tweak.ui.screen.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.component.Logo
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.utils.restartApp
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.model.stringResourceByRuntimeMode
import io.github.lumkit.tweak.model.stringResourceByRuntimeModeDescription
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_sharp
import tweak_alpha.shared.generated.resources.text_checking_runtime
import tweak_alpha.shared.generated.resources.text_restart
import tweak_alpha.shared.generated.resources.text_root_permisstion_denied
import tweak_alpha.shared.generated.resources.text_select_runtime_mode
import tweak_alpha.shared.generated.resources.toolkit_tweak

@Composable
internal fun SplashScreen(
    viewModel: SplashViewModel = viewModel { SplashViewModel() }
) {
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()
    val runtimeModes = remember { RuntimeMode.entries.filter { it != RuntimeMode.Unknow } }
    val checkLoadingState by viewModel.checkLoadingState.collectAsStateWithLifecycle()
    val runtimeMode by viewModel.runtimeModeState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, runtimeMode) {
        // 检查运行环境
        viewModel.checkRuntime {
            navigator.navigate(Screen.Main, true)
        }
    }

    ScreenSurface {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            popupHost = {
                CheckRuntimeDialog(checkLoadingState)
            },
            containerColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(it)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Logo(
                    modifier = Modifier.size(180.dp)
                        .padding(vertical = 24.dp)
                )

                Text(
                    text = stringResource(Res.string.toolkit_tweak),
                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.31f),
                    style = MiuixTheme.textStyles.headline1.copy(
                        fontSize = 28.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.text_select_runtime_mode),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.footnote2,
                )

                if ((runtimeMode == null || runtimeMode == RuntimeMode.Unknow) && !checkLoadingState) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        runtimeModes.onEach { mode ->
                            when (mode) {
                                RuntimeMode.Unknow -> Unit
                                RuntimeMode.Root -> {
                                    ModeItem(
                                        icon = {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_sharp),
                                                contentDescription = null
                                            )
                                        },
                                        title = stringResourceByRuntimeMode(mode),
                                        description = stringResourceByRuntimeModeDescription(mode),
                                        action = {
                                            scope.launch {
                                                if (viewModel.checkRootMode()) {
                                                    viewModel.setRuntimeMode(RuntimeMode.Root)
                                                    navigator.navigate(Screen.Main, true)
                                                } else {
                                                    val result = snackbarHostState.showSnackbar(
                                                        getString(Res.string.text_root_permisstion_denied),
                                                        actionLabel = getString(Res.string.text_restart),
                                                    )

                                                    when (result) {
                                                        SnackbarResult.Dismissed -> Unit
                                                        SnackbarResult.ActionPerformed -> {
                                                            restartApp()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeItem(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    action: () -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = action,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(28.dp)
                    .squircleSurface(
                        MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        6.dp
                    )
                    .padding(6.dp),
                color = Color.Transparent
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MiuixTheme.colorScheme.surface
                ) {
                    icon()
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.footnote1,
                )

                Text(
                    text = description,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .31f),
                    style = MiuixTheme.textStyles.footnote2,
                )
            }

            Icon(
                imageVector = MiuixIcons.Normal.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onSurface.copy(.5f),
            )
        }
    }
}

@Composable
private fun CheckRuntimeDialog(
    showState: Boolean
) {
    WindowDialog(
        show = showState,
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
                text = stringResource(Res.string.text_checking_runtime),
                style = MiuixTheme.textStyles.main
            )
        }
    }
}