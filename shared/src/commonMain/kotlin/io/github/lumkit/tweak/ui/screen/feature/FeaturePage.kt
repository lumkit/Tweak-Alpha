package io.github.lumkit.tweak.ui.screen.feature

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.logD
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

        val enabledFloatNavBar by GlobalViewModel.enabledFloatNavBar.collectAsStateWithLifecycle()
        val navBarBottomPadding by animateDpAsState(
            targetValue = if (enabledFloatNavBar) {
                28.dp
            } else {
                16.dp
            }
        )

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
                bottom = it.calculateBottomPadding() + NavigationBarHeight + navBarBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
    val feature by remember {
        derivedStateOf {
            provider.feature
        }
    }
    val featureState = remember(runtimeMode, feature) { feature.availableState(runtimeMode) }
    var enabled by remember(feature) { mutableStateOf(true) }
    val description = feature.ruleDescription()

    LaunchedEffect(feature, featureState) {
        val rule = feature.rule()
        val enable = featureState == FeatureState.ENABLED
        logD("FeatureItem: $rule, $enable")

        enabled = rule && enable
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .weight(1f),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer
        ),
        pressFeedbackType = if (!enabled) {
            PressFeedbackType.None
        } else {
            when (featureState) {
                FeatureState.ENABLED -> PressFeedbackType.Sink
                FeatureState.DISABLED -> PressFeedbackType.None
                FeatureState.HIDE -> PressFeedbackType.None
            }
        },
        onClick = {
            if (enabled) {
                onClick(feature.route)
            } else {
                scope.launch {
                    description?.let { hostState.showSnackbar(it) }
                }
            }
        },
    ) {

        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp)
                    .alpha(if (enabled) 1f else .31f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(feature.icon),
                    contentDescription = stringResource(feature.title),
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )

                Text(
                    text = stringResource(feature.title),
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