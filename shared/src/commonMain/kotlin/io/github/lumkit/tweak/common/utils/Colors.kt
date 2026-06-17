package io.github.lumkit.tweak.common.utils

import androidx.annotation.FloatRange
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MID_USED_THRESHOLD = 0.65f
private const val HIGH_USED_THRESHOLD = 0.85f

private val UsedColorAnimationSpec = tween<Color>(
    durationMillis = 120,
    easing = LinearEasing,
)

private enum class UsedColorLevel {
    DEFAULT,
    MID,
    HIGH,
}

@Composable
fun animatedColorAsUsed(
    @FloatRange(from = 0.0, to = 1.0) targetUsed: Float,
    defaultColor: Color = MiuixTheme.colorScheme.primary.copy(.75f),
    midColor: Color = Color(0xFFFC8A1B).copy(.75f),
    highColor: Color = Color(0xFFF9592F).copy(.75f),
    animationSpec: AnimationSpec<Color> = UsedColorAnimationSpec,
): State<Color> {
    val level = when {
        targetUsed > HIGH_USED_THRESHOLD -> UsedColorLevel.HIGH
        targetUsed > MID_USED_THRESHOLD -> UsedColorLevel.MID
        else -> UsedColorLevel.DEFAULT
    }

    val targetColor = remember(level, defaultColor, midColor, highColor) {
        when (level) {
            UsedColorLevel.HIGH -> highColor
            UsedColorLevel.MID -> midColor
            UsedColorLevel.DEFAULT -> defaultColor
        }
    }

    return animateColorAsState(
        targetValue = targetColor,
        animationSpec = animationSpec,
        label = "animatedColorAsUsed",
    )
}
