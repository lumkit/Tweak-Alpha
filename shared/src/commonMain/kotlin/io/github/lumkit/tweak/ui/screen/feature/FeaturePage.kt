package io.github.lumkit.tweak.ui.screen.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureCategory
import io.github.lumkit.tweak.ui.screen.feature.model.FeatureState
import io.github.lumkit.tweak.ui.screen.feature.model.availableState
import io.github.lumkit.tweak.ui.theme.NavigationBarHeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.nav_category

@Composable
fun FeaturePage() {
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val providers by FeatureRegistry.providers
    val runtimeMode by GlobalViewModel.runtimeModeState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val hostState = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(Res.string.nav_category),
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
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = it.calculateTopPadding() + 16.dp,
                bottom = it.calculateBottomPadding() + NavigationBarHeight + 28.dp,
            )
        ) {
            items(providers) { category ->
                FeatureCategoryItem(category, runtimeMode, hostState, scope, navigator::navigate)
            }
        }
    }
}

@Composable
private fun FeatureCategoryItem(
    category: FeatureCategory,
    runtimeMode: RuntimeMode?,
    hostState: SnackbarHostState,
    scope: CoroutineScope,
    onClick: (route: Screen) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SmallTitle(text = stringResource(category.title))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            category.features.chunked(2).onEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.onEach { provider ->
                        FeatureItem(provider, runtimeMode, hostState, scope, onClick)
                    }

                    repeat(2 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.FeatureItem(
    provider: FeatureProvider,
    runtimeMode: RuntimeMode?,
    hostState: SnackbarHostState,
    scope: CoroutineScope,
    onClick: (route: Screen) -> Unit,
) {
    val density = LocalDensity.current
    val featureState = remember(runtimeMode) { provider.feature.availableState(runtimeMode) }
    var enabled by remember { mutableStateOf(true) }
    val pressFeedbackType = remember(featureState, enabled) {
        if (!enabled) {
            PressFeedbackType.None
        } else {
            when (featureState) {
                FeatureState.ENABLED -> PressFeedbackType.Sink
                FeatureState.DISABLED -> PressFeedbackType.None
                FeatureState.HIDE -> PressFeedbackType.None
            }
        }
    }
    val description = provider.feature.ruleDescription()

    LaunchedEffect(provider.feature, featureState) {
        enabled = provider.feature.rule() && featureState == FeatureState.ENABLED
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .weight(1f),
        pressFeedbackType = pressFeedbackType,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer
        ),
        onClick = {
            if (enabled) {
                onClick(provider.feature.route)
            } else {
                scope.launch {
                    description?.let { hostState.showSnackbar(it) }
                }
            }
        },
    ) {
        var size by remember { mutableStateOf(DpSize.Zero) }

        Box(
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged {
                    size = with(density) { it.toSize().toDpSize() }
                },
        ) {
            Row(
                modifier = Modifier.padding(16.dp)
                    .alpha(if (enabled) 1f else .31f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(provider.feature.icon),
                    contentDescription = stringResource(provider.feature.title),
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )

                Text(
                    text = stringResource(provider.feature.title),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }

            if (!enabled) {
                Icon(
                    imageVector = MiuixIcons.Lock,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(4.dp)
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}