package io.github.lumkit.tweak.ui.screen.feature.model

import androidx.compose.runtime.Composable
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

data class FeatureCategory(
    val key: String,
    val title: StringResource,
    val features: Set<FeatureProvider>,
)

data class Feature(
    val key: String,
    val title: StringResource,
    val icon: DrawableResource,
    val description: StringResource,
    val capabilities: Set<Capability>,
    val route: Screen,
    val defaultState: FeatureState,
    val rule: suspend () -> Boolean = { true },
    val ruleDescription: @Composable () -> String?,
)

fun Feature.availableState(
    runtime: RuntimeMode?
): FeatureState {
    if (defaultState == FeatureState.HIDE || capabilities.isEmpty()) {
        return FeatureState.HIDE
    }
    return if (capabilities.all { it.available(runtime) }) {
        FeatureState.ENABLED
    } else {
        FeatureState.DISABLED
    }
}

private fun Capability.available(
    runtime: RuntimeMode?
): Boolean {
    return (runtime?.user ?: -1) >= this.runtime.user
}