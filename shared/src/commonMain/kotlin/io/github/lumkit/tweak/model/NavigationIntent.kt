package io.github.lumkit.tweak.model

import kotlinx.serialization.Serializable

@Serializable
enum class NavigationIntentTargetScreen {
    UpdateSystem,
    FpsRecord,
    FlashRom,
}

@Serializable
data class NavigationIntent(
    val targetScreen: NavigationIntentTargetScreen,
    val screenJson: String
)
