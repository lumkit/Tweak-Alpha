package io.github.lumkit.tweak.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen: NavKey {

    @Serializable
    data object Splash: Screen()

    @Serializable
    data object Main: Screen()

    @Serializable
    data object UpdateSystem: Screen()

    @Serializable
    data object FpsRecord: Screen()

    @Serializable
    data class FpsRecordDetail(val id: Long): Screen()

}