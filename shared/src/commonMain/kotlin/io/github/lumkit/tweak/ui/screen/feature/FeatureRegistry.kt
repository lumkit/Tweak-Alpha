package io.github.lumkit.tweak.ui.screen.feature

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureCategory
import io.github.lumkit.tweak.ui.screen.fpsRecord.FpsRecordingProvider
import io.github.lumkit.tweak.ui.screen.updateSys.UpdateSystemProvider
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_feature_performance
import tweak_alpha.shared.generated.resources.text_feature_system

object FeatureRegistry {
    private val _providers = mutableStateOf<List<FeatureCategory>>(emptyList())
    val providers: State<List<FeatureCategory>> = _providers

    fun registerProvider(provider: FeatureCategory) {
        _providers.value += provider
    }

    fun unregisterProvider(provider: FeatureCategory) {
        _providers.value -= provider
    }

    init {
        // 系统功能
        registerProvider(
            FeatureCategory(
                key = "system",
                title = Res.string.text_feature_system,
                features = setOf(
                    UpdateSystemProvider,
                )
            )
        )

        // 设备性能
        registerProvider(
            FeatureCategory(
            key = "performance",
                title = Res.string.text_feature_performance,
                features = setOf(
                    FpsRecordingProvider,
                )
            )
        )
    }
}