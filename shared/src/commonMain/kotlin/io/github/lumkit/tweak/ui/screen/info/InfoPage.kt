package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lumkit.tweak.common.component.CategoryCard
import io.github.lumkit.tweak.ui.theme.ContentSafeHorizontalPadding
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import tweak.shared.generated.resources.Res
import tweak.shared.generated.resources.text_cpu_state

@Composable
fun InfoPage() {
    val density = LocalDensity.current
    val loadState by DeviceInfoViewModel.loadingState.collectAsStateWithLifecycle()
    val blurDp by animateDpAsState(
        targetValue = if (loadState) 0.dp else 15.dp,
        animationSpec = tween(durationMillis = 400)
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .blur(blurDp),
            contentPadding = PaddingValues(
                top = with(density) {
                    16.dp + WindowInsets.statusBars.getTop(this).toDp()
                },
                bottom = with(density) {
                    NavigationBarHeight + 28.dp + WindowInsets.navigationBars.getBottom(this).toDp()
                },
                start = ContentSafeHorizontalPadding,
                end = ContentSafeHorizontalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CpuInfoContent()
            }
        }

        AnimatedContent(
            targetState = loadState,
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) {
            if (!it) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = null
                        ){},
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CpuInfoContent() {
    val cpuState by DeviceInfoViewModel.cpuInfoState.collectAsStateWithLifecycle()

    CategoryCard(
        title = stringResource(Res.string.text_cpu_state),
        modifier = Modifier.fillMaxWidth()
    ) {

    }
}