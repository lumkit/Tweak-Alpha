package io.github.lumkit.tweak.ui.screen.updateSys

import androidx.compose.runtime.Composable
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_update
import tweak_alpha.shared.generated.resources.text_feature_update_system
import tweak_alpha.shared.generated.resources.text_feature_update_system_description
import tweak_alpha.shared.generated.resources.text_go_back

internal val UpdateSystemProvider = object : FeatureProvider {
    override val feature: Feature
        get() = Feature(
            key = "UpdateSystemProvider",
            title = Res.string.text_feature_update_system,
            icon = Res.drawable.ic_update,
            description = Res.string.text_feature_update_system_description,
            capabilities = setOf(
                Capability.ROOT_ONLY,
            ),
            route = Screen.UpdateSystem,
            defaultState = FeatureState.ENABLED,
        )

    @Composable
    override fun Content() {
        UpdateSystemScreen()
    }

}

@Composable
fun UpdateSystemScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val navigator = LocalNavigator.current

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.text_feature_update_system),
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                navigationIcon = {
                    IconButton(
                        onClick = navigator::goBack
                    ) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(Res.string.text_go_back),
                        )
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) {

    }
}