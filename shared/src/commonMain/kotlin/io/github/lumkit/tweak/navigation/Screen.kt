package io.github.lumkit.tweak.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen: NavKey {

    @Serializable
    data object Splash: Screen()

    @Serializable
    data object Main: Screen()
}