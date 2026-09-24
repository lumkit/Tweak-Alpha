package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import io.github.lumkit.tweak.common.utils.isAdvancedRenderEffectSupported
import io.github.lumkit.tweak.model.AppearanceSettingsStore
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun Modifier.glassBlur(
    backdrop: Backdrop,
    backgroundColor: Color = MiuixTheme.colorScheme.surface,
    shape: Shape = RoundedCornerShape(0.dp),
    shadow: Shadow = Shadow(radius = 0.dp)
) = composed {
    val enabled by AppearanceSettingsStore.enabledBlur.collectAsStateWithLifecycle()
    val supported = remember { isAdvancedRenderEffectSupported() }
    val advancedBackdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }

    this.drawBackdrop(
        backdrop = backdrop,
        shape = {
            shape
        },
        effects = {
            if (advancedBackdropEffectSupported && enabled) {
                vibrancy()
            }
            if (supported && enabled) {
                blur(8f.dp.toPx())
            }
            if (advancedBackdropEffectSupported && enabled) {
                lens(8f.dp.toPx(), 8f.dp.toPx())
            }
        },
        shadow = { shadow },
        onDrawSurface = {
            if (supported && enabled) {
                drawRect(backgroundColor.copy(.4f))
            } else {
                drawRect(backgroundColor)
            }
        },
        highlight = { null }
    )
}