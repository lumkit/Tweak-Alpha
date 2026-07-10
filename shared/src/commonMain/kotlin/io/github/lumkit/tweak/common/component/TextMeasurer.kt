package io.github.lumkit.tweak.common.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

@Composable
fun rememberTextWidth(
    textStyle: TextStyle,
    text: String
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(textStyle, text) {
        with(density) {
            textMeasurer.measure(
                text = text,
                style = textStyle
            ).size.width.toDp()
        }
    }
}