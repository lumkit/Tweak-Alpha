package io.github.lumkit.tweak.ui.screen.updateSys

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.feature.UpdateEngineClient
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.FeatureProvider
import io.github.lumkit.tweak.ui.screen.feature.model.Capability
import io.github.lumkit.tweak.ui.screen.feature.model.Feature
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_update
import tweak_alpha.shared.generated.resources.text_feature_rule_description_update_sys
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
            rule = {
                runCatching {
                    UpdateEngineClient.support()
                }.getOrNull() ?: false
            },
            ruleDescription = {
                stringResource(Res.string.text_feature_rule_description_update_sys)
            }
        )

    @Composable
    override fun Content() {
        UpdateSystemScreen()
    }

}

@Composable
fun UpdateSystemScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backdrop = rememberLayerBackdropColor()

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
        Column(
            modifier = Modifier.fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(backdrop)
                .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    UpdateEngineViewModel.installRom("/storage/emulated/0/Download/DLManager/aurora-ota_full-OS3.0.305.0.WNACNXM-user-16.0-366972ea82.zip")
                }
            ) {
                Text("安装测试")
            }
        }
    }
}