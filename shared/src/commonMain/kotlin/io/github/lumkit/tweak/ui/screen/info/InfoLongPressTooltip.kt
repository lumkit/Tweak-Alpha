package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.RichTooltip
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ValueRichTooltipBox(
    title: String,
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gestures = LocalInfoSectionGestures.current
    val card = remember { InfoLongPressCard() }
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val colors = TooltipDefaults.richTooltipColors()

    DisposableEffect(gestures, card, state, scope) {
        card.show = { scope.launch { state.show() } }
        card.dismiss = { state.dismiss() }
        gestures.attach(card)
        onDispose {
            gestures.detach(card)
            card.show = null
            card.dismiss = null
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            card.boundsInRoot = coordinates.boundsInRoot()
        },
    ) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above,
        ),
        tooltip = {
            RichTooltip(
                title = {
                    Text(
                        text = title,
                        color = colors.titleContentColor,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                },
                colors = colors,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { (label, value) ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                color = colors.contentColor,
                                style = MiuixTheme.textStyles.body2,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = value,
                                color = colors.titleContentColor,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }
        },
        state = state,
        focusable = true,
        enableUserInput = false,
    ) {
        content()
    }
    }
}
