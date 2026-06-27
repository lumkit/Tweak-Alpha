package io.github.lumkit.tweak.ui.screen.updateSys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.SDK_INT
import io.github.lumkit.tweak.common.utils.ZipEntry
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.logE
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
import top.yukonga.miuix.kmp.basic.Text
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
            rule = {
                // Update功能只在Android11以上引入
                SDK_INT >= 30
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
        var entries by remember { mutableStateOf(emptyList<ZipEntry>()) }

        LaunchedEffect(Unit) {
            entries = Files.zipEntries("/storage/emulated/0/Download/DLManager/aurora-ota_full-OS3.0.305.0.WNACNXM-user-16.0-366972ea82.zip")
                .also {
                    if (it is NativeFileResult.Failure) {
                        logE(it.toString(), null, "zipEntries")
                    }
                }
                .getOrNull() ?: emptyList()
        }

        LazyColumn(
            modifier = Modifier.padding(it),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) {
                Text(it.toString())
            }
        }
    }
}