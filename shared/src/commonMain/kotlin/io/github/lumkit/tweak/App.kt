package io.github.lumkit.tweak

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.github.lumkit.tweak.common.utils.ripple
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Navigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.navigation.rememberNavigationState
import io.github.lumkit.tweak.ui.screen.feature.FeatureRegistry
import io.github.lumkit.tweak.ui.screen.main.MainScreen
import io.github.lumkit.tweak.ui.screen.splash.SplashScreen
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
@Preview
fun App() {
    val themeViewModel = viewModel { ThemeViewModel() }

    TweakTheme(
        viewModel = themeViewModel,
    ) {
        val backgroundColor = MiuixTheme.colorScheme.surface

        GlobalCompositionProvider {
            val snackbarHostState = LocalSnackBarHostState.current

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = backgroundColor,
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    snackbarHost = {
                        SnackbarHost(state = snackbarHostState)
                    }
                ) {
                    AppRoute(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

val LocalSnackBarHostState = staticCompositionLocalOf<SnackbarHostState> { error("not provided.") }

@Composable
private fun GlobalCompositionProvider(
    content: @Composable () -> Unit
) {

    val navigationState = rememberNavigationState(startRoute = Screen.Splash)
    val navigator = remember { Navigator(navigationState) }
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalSnackBarHostState provides snackbarHostState,
        LocalIndication provides ripple(color = MiuixTheme.colorScheme.onSurface),
        content = content,
    )
}

@Composable
private fun AppRoute(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val navigationState = remember(navigator) { navigator.state }
    val provides by FeatureRegistry.providers
    val featureProviders = remember(provides) { provides.flatMap { it.features } }

    val entryProvider = remember(provides) {
        entryProvider<NavKey> {
            entry<Screen.Splash> {
                SplashScreen()
            }
            entry<Screen.Main> {
                MainScreen()
            }
            featureProviders.onEach { featureProvider ->
                entry(featureProvider.feature.route) {
                    featureProvider.Content()
                }
            }
        }
    }

    NavDisplay(
        modifier = modifier,
        backStack = navigator.state.backStacks[navigationState.topLevelRoute] ?: emptyList(),
        transitionSpec = {
            // Slide in from right when navigating forward
            (slideInHorizontally(
                animationSpec = tween(durationMillis = 400),
                initialOffsetX = { it })
                    ) togetherWith (slideOutHorizontally(
                animationSpec = tween(durationMillis = 400),
                targetOffsetX = { (-it * .31f).roundToInt() }) + scaleOut(
                targetScale = .75f,
                animationSpec = tween(durationMillis = 400)
            ) + fadeOut(animationSpec = tween(durationMillis = 400), targetAlpha = .75f))
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            (slideInHorizontally(
                animationSpec = tween(durationMillis = 400),
                initialOffsetX = { (-it * .31f).roundToInt() }) +
                    scaleIn(animationSpec = tween(durationMillis = 400), initialScale = .75f) +
                    fadeIn(animationSpec = tween(durationMillis = 400), initialAlpha = .75f)
                    ) togetherWith (slideOutHorizontally(
                animationSpec = tween(durationMillis = 400),
                targetOffsetX = { it }))
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            (slideInHorizontally(
                animationSpec = tween(durationMillis = 400),
                initialOffsetX = { (-it * .31f).roundToInt() }) +
                    scaleIn(animationSpec = tween(durationMillis = 400), initialScale = .75f) +
                    fadeIn(animationSpec = tween(durationMillis = 400), initialAlpha = .75f)
                    ) togetherWith (slideOutHorizontally(
                animationSpec = tween(durationMillis = 400),
                targetOffsetX = { it }))
        },
        entryProvider = entryProvider,
    )
}