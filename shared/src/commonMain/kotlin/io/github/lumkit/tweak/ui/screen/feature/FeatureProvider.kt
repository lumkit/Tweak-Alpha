package io.github.lumkit.tweak.ui.screen.feature

import androidx.compose.runtime.Composable
import io.github.lumkit.tweak.ui.screen.feature.model.Feature

interface FeatureProvider {

    val feature: Feature

    @Composable
    fun Content()

}