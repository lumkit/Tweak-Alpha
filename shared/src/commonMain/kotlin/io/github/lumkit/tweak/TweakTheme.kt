package io.github.lumkit.tweak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun TweakTheme(
    fontFamily: FontFamily? = null,
    viewModel: ThemeViewModel = viewModel { ThemeViewModel() },
    content: @Composable () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val controller = remember(themeMode) { ThemeController(themeMode) }

    return MiuixTheme(
        controller = controller,
        content = content,
        textStyles = MiuixTheme.textStyles.let {
            if (fontFamily == null) {
                it
            } else {
                it.copy(
                    main = MiuixTheme.textStyles.main.copy(
                        fontFamily = fontFamily
                    ),
                    paragraph = MiuixTheme.textStyles.paragraph.copy(
                        fontFamily = fontFamily
                    ),
                    body1 = MiuixTheme.textStyles.body1.copy(
                        fontFamily = fontFamily
                    ),
                    body2 = MiuixTheme.textStyles.body2.copy(
                        fontFamily = fontFamily
                    ),
                    button = MiuixTheme.textStyles.button.copy(
                        fontFamily = fontFamily
                    ),
                    footnote1 = MiuixTheme.textStyles.footnote1.copy(
                        fontFamily = fontFamily
                    ),
                    footnote2 = MiuixTheme.textStyles.footnote2.copy(
                        fontFamily = fontFamily
                    ),
                    headline1 = MiuixTheme.textStyles.headline1.copy(
                        fontFamily = fontFamily
                    ),
                    headline2 = MiuixTheme.textStyles.headline2.copy(
                        fontFamily = fontFamily
                    ),
                    subtitle = MiuixTheme.textStyles.subtitle.copy(
                        fontFamily = fontFamily
                    ),
                    title1 = MiuixTheme.textStyles.title1.copy(
                        fontFamily = fontFamily
                    ),
                    title2 = MiuixTheme.textStyles.title2.copy(
                        fontFamily = fontFamily
                    ),
                    title3 = MiuixTheme.textStyles.main.copy(
                        fontFamily = fontFamily
                    ),
                    title4 = MiuixTheme.textStyles.title4.copy(
                        fontFamily = fontFamily
                    )
                )
            }
        },
    )
}