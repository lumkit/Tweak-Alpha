package io.github.lumkit.tweak.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.openUrl
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_open_sources

@Composable
fun OpenSourceScreen(
    viewModel: OpenSourceViewModel = viewModel { OpenSourceViewModel() }
) {

    val list by viewModel.licenseState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.text_open_sources),
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
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) {
        val padding = remember(it) {
            PaddingValues(
                start = it.calculateLeftPadding(direction),
                end = it.calculateRightPadding(direction),
            )
        }

        LazyColumn(
            modifier = Modifier.layerBackdrop(backdrop)
                .padding(padding)
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = it.calculateStartPadding(direction),
                top = it.calculateTopPadding(),
                end = it.calculateEndPadding(direction) + 16.dp,
                bottom = it.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list) { bean ->
                TooltipBox(text = bean.tip) {
                    ArrowPreference(
                        title = bean.title,
                        summary = "${bean.author}\n${bean.tip}",
                        onClick = {
                            openUrl(bean.url)
                        }
                    )
                }
            }
        }
    }

}