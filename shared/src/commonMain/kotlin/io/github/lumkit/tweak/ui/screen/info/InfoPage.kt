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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lumkit.tweak.common.component.CategoryCard
import io.github.lumkit.tweak.common.component.LintStackChart
import io.github.lumkit.tweak.common.component.rememberChartState
import io.github.lumkit.tweak.ui.theme.ContentSafeHorizontalPadding
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import tweak.shared.generated.resources.Res
import tweak.shared.generated.resources.text_cpu_state
import tweak.shared.generated.resources.text_cpu_state_description

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
    val chartState = rememberChartState()

    val listener: (DeviceInfoViewModel.CpuInfoModel) -> Unit = remember {
        {
            chartState.push(it.coreLoad ?: 0f)
        }
    }

    DisposableEffect(listener) {
        DeviceInfoViewModel.addCpuInfoUpdateListener(listener)

        onDispose {
            DeviceInfoViewModel.removeCpuInfoUpdateListener(listener)
        }
    }

    CategoryCard(
        title = stringResource(Res.string.text_cpu_state),
        subTitle = stringResource(Res.string.text_cpu_state_description).format(
            cpuState?.coreLoadText ?: "N/A", cpuState?.coreTemperatureText ?: "N/A"
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(64.dp),
        ) {

            LintStackChart(
                modifier = Modifier.fillMaxSize()
                    .alpha(.4f),
                state = chartState,
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = cpuState?.socName ?: "N/A",
                    color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                    style = MiuixTheme.textStyles.footnote1,
                )
                Text(
                    text = "Core ${cpuState?.coreCluster}",
                    color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxWidth()
            .padding(vertical = 8.dp))

        CpuCoreContent()
    }
}

@Composable
private fun CpuCoreContent() {
    val cpuState by DeviceInfoViewModel.cpuInfoState.collectAsStateWithLifecycle()
    val columns by remember { derivedStateOf { cpuState?.cpuStates?.size?.div(2) ?: 4 } }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cpuState?.cpuStates?.forEach { core ->
            CpuCoreItem(core)
        }
    }
}

@Composable
private fun FlowRowScope.CpuCoreItem(core: DeviceInfoViewModel.CoreInfoModel) {
    val chartState = rememberChartState()

    LaunchedEffect(core) {
        chartState.push(core.load)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .height(75.dp)
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            LintStackChart(
                modifier = Modifier.fillMaxSize()
                    .alpha(.4f),
                state = chartState,
            )

            Text(
                text = core.loadText,
                color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                style = MiuixTheme.textStyles.footnote2,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = core.currentFreq,
            color = MiuixTheme.colorScheme.onSurface.copy(.7f),
            style = MiuixTheme.textStyles.body2,
        )

        Text(
            text = "${core.minFreq}~${core.maxFreq}",
            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}
