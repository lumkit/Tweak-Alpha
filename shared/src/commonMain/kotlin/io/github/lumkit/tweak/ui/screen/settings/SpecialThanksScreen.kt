package io.github.lumkit.tweak.ui.screen.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.Rectangle
import com.kyant.shapes.copy
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.openUrl
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.navigation.LocalNavigator
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_go_back
import tweak_alpha.shared.generated.resources.text_special_thanks
import tweak_alpha.shared.generated.resources.text_special_thanks_contact

@Composable
fun SpecialThanksScreen(
    viewModel: SpecialThanksViewModel = viewModel { SpecialThanksViewModel() },
) {
    val list by viewModel.contributors.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.text_special_thanks),
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
            LazyColumn(
                modifier = Modifier.layerBackdrop(backdrop)
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = it.calculateStartPadding(direction),
                    top = it.calculateTopPadding(),
                    end = it.calculateEndPadding(direction),
                    bottom = it.calculateBottomPadding() + 16.dp
                ),
            ) {
                items(list) { bean ->
                    val contactLine = if (bean.contact.isNotBlank()) {
                        stringResource(Res.string.text_special_thanks_contact, bean.contact)
                    } else {
                        ""
                    }
                    ArrowPreference(
                        title = bean.nickname,
                        summary = buildString {
                            if (bean.contribution.isNotBlank()) {
                                append(bean.contribution)
                            }
                            if (contactLine.isNotBlank()) {
                                if (isNotEmpty()) append('\n')
                                append(contactLine)
                            }
                        },
                        startAction = {
                            AsyncImage(
                                model = bean.avatarUrl,
                                contentDescription = bean.nickname,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(Rectangle.copy(cornerRadius = 12.dp))
                                    .border(.75.dp, shape = Rectangle.copy(cornerRadius = 12.dp), color = MiuixTheme.colorScheme.dividerLine),
                                onError = { state ->
                                    logE(
                                        "avatar load failed url=${bean.avatarUrl}: ${state.result.throwable.message}",
                                        state.result.throwable,
                                    )
                                },
                            )
                        },
                        onClick = {
                            openContributorContact(bean.contact)
                        },
                    )
                }
            }
        }
    }
}

private fun openContributorContact(contact: String) {
    val value = contact.trim()
    if (value.isEmpty()) return
    val url = when {
        value.all { it.isDigit() } ->
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$value"
        else -> value
    }
    openUrl(url)
}
