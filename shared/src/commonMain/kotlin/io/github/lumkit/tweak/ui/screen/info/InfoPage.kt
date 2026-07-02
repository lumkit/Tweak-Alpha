package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.common.component.CategoryCard
import io.github.lumkit.tweak.common.component.LintStackChart
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.component.rememberChartState
import io.github.lumkit.tweak.common.utils.animatedColorAsBattery
import io.github.lumkit.tweak.common.utils.animatedColorAsUsed
import io.github.lumkit.tweak.common.utils.isAdvancedBackdropEffectSupported
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.nav_home
import tweak_alpha.shared.generated.resources.text_battery
import tweak_alpha.shared.generated.resources.text_cpu_state
import tweak_alpha.shared.generated.resources.text_cpu_state_description
import tweak_alpha.shared.generated.resources.text_gpu_state
import tweak_alpha.shared.generated.resources.text_memory_physical
import tweak_alpha.shared.generated.resources.text_memory_state
import tweak_alpha.shared.generated.resources.text_storage
import tweak_alpha.shared.generated.resources.text_storage_flash_type
import tweak_alpha.shared.generated.resources.text_storage_free
import tweak_alpha.shared.generated.resources.text_storage_total
import tweak_alpha.shared.generated.resources.text_swap
import tweak_alpha.shared.generated.resources.text_total_memory
import tweak_alpha.shared.generated.resources.text_total_memory_used
import tweak_alpha.shared.generated.resources.text_used_load

@Composable
fun InfoPage() {
    val loadState by DeviceInfoViewModel.loadingState.collectAsStateWithLifecycle()
    val blurDp by animateDpAsState(
        targetValue = if (loadState) 0.dp else 15.dp,
        animationSpec = tween(durationMillis = 400)
    )
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val backdropEffectSupported = remember { isAdvancedBackdropEffectSupported() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.nav_home),
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
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
                    .then(if (blurDp > 0.dp) {
                        Modifier.blur(blurDp)
                    } else {
                        Modifier
                    })
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = it.calculateTopPadding() + 16.dp,
                    bottom = it.calculateBottomPadding() + NavigationBarHeight + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    CpuInfoContent()
                }

                item {
                    MemoryInfoContent()
                }

                item {
                    GpuInfoContent()
                }

                item {
                    MoreInfoContent()
                }
            }
        }

        val alpha by animateFloatAsState(
            targetValue = if (loadState) 0f else .5f,
            animationSpec = tween(durationMillis = 400)
        )

        if (alpha > 0f) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .then(
                        if (!loadState) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = null,
                            ) {}
                        } else {
                            Modifier
                        }
                    )
                    .alpha(alpha)
                    .background(
                        color = if (backdropEffectSupported) {
                            Color.Transparent
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
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
                    text = "Cores ${cpuState?.coreCluster}",
                    color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 8.dp)
        )

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

@Composable
private fun MemoryInfoContent() {
    val memoryState by DeviceInfoViewModel.memoryInfoState.collectAsStateWithLifecycle()

    val load by animateFloatAsState(targetValue = memoryState?.totalUsed ?: 0f)
    val memoryLoad by animateFloatAsState(targetValue = memoryState?.memoryUsed ?: 0f)
    val swapLoad by animateFloatAsState(targetValue = memoryState?.swapUsed ?: 0f)

    val loadColor by animatedColorAsUsed(load)
    val memoryLoadColor by animatedColorAsUsed(memoryLoad)
    val swapLoadColor by animatedColorAsUsed(swapLoad)

    CategoryCard(
        title = stringResource(Res.string.text_memory_state)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = load,
                    size = 80.dp,
                    strokeWidth = 16.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = loadColor
                    ),
                )

                Text(
                    text = stringResource(Res.string.text_total_memory),
                    color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                    style = MiuixTheme.textStyles.body2,
                )
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight()
                    .padding(horizontal = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = memoryLoad,
                            height = 8.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = memoryLoadColor
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MemoryTable(
                            title = stringResource(Res.string.text_memory_physical),
                            content = buildString {
                                append(memoryState?.memoryUsedText ?: "N/A")
                                append(" (")
                                append(memoryState?.memorySizeUnitText ?: "N/A")
                                append(")")
                            },
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = swapLoad,
                            height = 8.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = swapLoadColor
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MemoryTable(
                            title = stringResource(Res.string.text_swap),
                            content = buildString {
                                append(memoryState?.swapUsedText ?: "N/A")
                                append(" (")
                                append(memoryState?.swapSizeUnitText ?: "N/A")
                                append(")")
                            },
                        )
                    }
                    // TODO 操作按钮
                }

                Row {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(Res.string.text_total_memory_used))
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = MiuixTheme.colorScheme.onSurface.copy(.31f)
                                )
                            ) {
                                append(memoryState?.totalUsedText ?: "N/A")
                            }
                        },
                        color = MiuixTheme.colorScheme.onSurface.copy(.7f),
                        style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = buildAnnotatedString {
                            append("SwapCached ")
                            withStyle(
                                SpanStyle(
                                    color = MiuixTheme.colorScheme.onSurface.copy(.31f)
                                )
                            ) {
                                append(memoryState?.swapCacheUnitText ?: "N/A")
                            }
                        },
                        color = MiuixTheme.colorScheme.onSurface.copy(.7f),
                        style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryTable(
    title: String,
    content: String,
) {
    Row {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface.copy(.7f),
            style = MiuixTheme.textStyles.footnote2,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.width(28.dp))

        Text(
            text = content,
            color = MiuixTheme.colorScheme.onSurface.copy(.31f),
            style = MiuixTheme.textStyles.footnote2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GpuInfoContent() {
    val density = LocalDensity.current
    val translateY = remember {
        with(density) {
            (-1.5f).dp.toPx()
        }
    }
    val gpuInfoModel by DeviceInfoViewModel.gpuInfoState.collectAsStateWithLifecycle()
    val load by animateFloatAsState(targetValue = gpuInfoModel?.load ?: 0f)
    val loadColor by animatedColorAsUsed(load)
    val gpuSupportedState by DeviceInfoViewModel.gpuSupported.collectAsStateWithLifecycle()

    CategoryCard(
        title = stringResource(Res.string.text_gpu_state)
    ) {
        if (gpuSupportedState) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = load,
                        size = 80.dp,
                        strokeWidth = 16.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = loadColor
                        ),
                    )

                    Text(
                        text = stringResource(Res.string.text_used_load),
                        color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                        style = MiuixTheme.textStyles.body2,
                    )
                }

                VerticalDivider(
                    modifier = Modifier.fillMaxHeight()
                        .padding(horizontal = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = gpuInfoModel?.currentFreq ?: "N/A",
                            color = MiuixTheme.colorScheme.onSurface.copy(.7f),
                            style = MiuixTheme.textStyles.body1,
                        )

                        Text(
                            text = gpuInfoModel?.freqRangeText ?: "N/A",
                            color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                            style = MiuixTheme.textStyles.footnote2,
                            modifier = Modifier.padding(start = 4f.dp)
                                .graphicsLayer {
                                    translationY = translateY
                                },
                        )
                    }

                    Text(
                        text = gpuInfoModel?.loadText ?: "N/A",
                        color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                        style = MiuixTheme.textStyles.footnote2
                            .copy(
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                            ),
                    )

                    Text(
                        text = gpuInfoModel?.displayInfo ?: "N/A",
                        color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                        style = MiuixTheme.textStyles.footnote2
                            .copy(
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                            ),
                    )
                }
            }
        } else {
            Text(
                text = gpuInfoModel?.displayInfo ?: "N/A",
                color = MiuixTheme.colorScheme.onSurface.copy(.31f),
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }
}

@Composable
private fun MoreInfoContent() {
    val moreInfoModel by DeviceInfoViewModel.moreInfoState.collectAsStateWithLifecycle()

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        BatteryContent(moreInfoModel?.battery)
        StorageContent(moreInfoModel?.storage)

    }
}

@Composable
private fun FlowRowScope.BatteryContent(
    batteryModel: DeviceInfoViewModel.BatteryInfoModel?,
) {
    val load by animateFloatAsState(targetValue = batteryModel?.capacity ?: 0f)
    val loadColor by animatedColorAsBattery(load)

    CategoryCard(
        title = stringResource(Res.string.text_battery),
        modifier = Modifier.fillMaxWidth()
            .weight(1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = load,
                    size = 48.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = loadColor
                    ),
                    strokeWidth = 8.dp,
                )
                Text(
                    text = batteryModel?.capacityText ?: "N/A",
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 10.sp,
                        lineHeight = 10.sp
                    ),
                    color = MiuixTheme.colorScheme.onSurface.copy(.5f)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row {
                    Text(
                        text = batteryModel?.levelText ?: "N/A",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                    )
                    Spacer(modifier = Modifier.weight(1f).defaultMinSize(minWidth = 4.dp))
                    Text(
                        text = batteryModel?.currentText ?: "N/A",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                    )
                }
                Row {
                    Text(
                        text = batteryModel?.temperatureText ?: "N/A",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                    )
                    Spacer(modifier = Modifier.weight(1f).defaultMinSize(minWidth = 4.dp))
                    Text(
                        text = batteryModel?.powerText ?: "N/A",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowRowScope.StorageContent(
    storageModel: DeviceInfoViewModel.StorageInfoModel?,
) {
    val load by animateFloatAsState(targetValue = storageModel?.usedLoad ?: 0f)
    val loadColor by animatedColorAsUsed(load)

    CategoryCard(
        title = stringResource(Res.string.text_storage),
        modifier = Modifier.fillMaxWidth()
            .weight(1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = load,
                    size = 48.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = loadColor
                    ),
                    strokeWidth = 8.dp,
                )
                Text(
                    text = storageModel?.usedText ?: "N/A",
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 10.sp,
                        lineHeight = 10.sp
                    ),
                    color = MiuixTheme.colorScheme.onSurface.copy(.5f),
                    textAlign = TextAlign.Center
                )
            }

            Column {
                Text(
                    text = stringResource(Res.string.text_storage_free)
                        .format(storageModel?.freeText ?: "N/A"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                )
                Text(
                    text = stringResource(Res.string.text_storage_total)
                        .format(storageModel?.totalText ?: "N/A"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                )
                Text(
                    text = stringResource(Res.string.text_storage_flash_type)
                        .format(storageModel?.flashType ?: "N/A"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(.8f)
                )
            }
        }
    }
}