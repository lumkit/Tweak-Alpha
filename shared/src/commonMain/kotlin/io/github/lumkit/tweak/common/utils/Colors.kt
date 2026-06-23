package io.github.lumkit.tweak.common.utils

import androidx.annotation.FloatRange
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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


@Immutable
class BatteryColors private constructor(
    private val colors: Array<Color>,
) {

    init {
        require(colors.size == 11) {
            "BatteryColors requires exactly 11 colors (0%~100%)."
        }
    }

    operator fun get(
        @FloatRange(from = 0.0, to = 1.0)
        used: Float,
    ): Color {
        val value = (1f - used.coerceIn(0f, 1f)) * 10f
        val index = value.toInt().coerceIn(0, 9)
        return lerp(
            start = colors[index],
            stop = colors[index + 1],
            fraction = value - index,
        )
    }

    companion object {

        val Default = BatteryColors(
            arrayOf(
                Color(0xFF4CAF50),
                Color(0xFF66BB6A),
                Color(0xFF8BC34A),
                Color(0xFFAED581),
                Color(0xFFDCE775),
                Color(0xFFFFEE58),
                Color(0xFFFFCA28),
                Color(0xFFFFA726),
                Color(0xFFFF7043),
                Color(0xFFF4511E),
                Color(0xFFD32F2F),
            )
        )

        operator fun invoke(
            p0: Color,
            p10: Color,
            p20: Color,
            p30: Color,
            p40: Color,
            p50: Color,
            p60: Color,
            p70: Color,
            p80: Color,
            p90: Color,
            p100: Color,
        ) = BatteryColors(
            arrayOf(
                p0,
                p10,
                p20,
                p30,
                p40,
                p50,
                p60,
                p70,
                p80,
                p90,
                p100,
            )
        )
    }
}

@Composable
fun animatedColorAsBattery(
    @FloatRange(from = 0.0, to = 1.0)
    targetUsed: Float,
    colors: BatteryColors = BatteryColors.Default,
    animationSpec: AnimationSpec<Color> = UsedColorAnimationSpec,
    alpha: Float = .75f,
): State<Color> {
    val color = remember(targetUsed, alpha) {
        colors[targetUsed].copy(alpha)
    }

    return animateColorAsState(
        targetValue = color,
        animationSpec = animationSpec,
    )
}