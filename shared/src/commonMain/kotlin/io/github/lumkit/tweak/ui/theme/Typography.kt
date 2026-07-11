package io.github.lumkit.tweak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.jet_brains_mono_regular


@Composable
fun getJetBrainsMonoRegularFontFamily(): FontFamily {
    return FontFamily(
        Font(Res.font.jet_brains_mono_regular),
    )
}