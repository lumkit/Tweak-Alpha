package io.github.lumkit.tweak.common.component

import androidx.compose.runtime.Composable

@Composable
fun Block(
    content: @Composable () -> Unit
) {
    content()
}