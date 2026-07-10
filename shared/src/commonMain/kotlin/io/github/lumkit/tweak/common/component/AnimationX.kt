package io.github.lumkit.tweak.common.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.isActive

@Composable
fun Modifier.breathingAlpha(
    enabled: Boolean,
    minAlpha: Float = 0.2f,
    duration: Int = 1200
): Modifier = composed {
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(enabled) {
        if (enabled) {
            while (isActive) {
                alpha.animateTo(
                    targetValue = minAlpha,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = LinearEasing
                    )
                )

                alpha.animateTo(
                    targetValue = .5f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = LinearEasing
                    )
                )
            }
        } else {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    alpha(alpha.value)
}