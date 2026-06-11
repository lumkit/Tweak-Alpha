package io.github.lumkit.tweak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun TweakTheme(
    viewModel: ThemeViewModel = viewModel { ThemeViewModel() },
    content: @Composable () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val controller = remember(themeMode) { ThemeController(themeMode) }

    return MiuixTheme(
        controller = controller,
        content = content
    )
}