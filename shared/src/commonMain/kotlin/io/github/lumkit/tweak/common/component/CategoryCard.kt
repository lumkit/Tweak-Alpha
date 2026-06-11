package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun CategoryCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        SmallTitle(text = title)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            content()
        }
    }
}
