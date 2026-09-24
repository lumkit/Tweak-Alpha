package io.github.lumkit.tweak.model

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.logI
import io.github.lumkit.tweak.navigation.Navigator
import io.github.lumkit.tweak.navigation.Screen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

object NavigationViewModel : BaseViewModel() {

    private val pendingScreens = MutableSharedFlow<Screen>(replay = 1, extraBufferCapacity = 1)

    fun navigate(screen: Screen) {
        if (!screen.isDeeplinkNavigable()) {
            return
        }
        pendingScreens.tryEmit(screen)
    }

    fun navigate(intent: NavigationIntent) {
        val screen = NavigationIntent.decodeScreen(intent.screenJson) ?: return
        navigate(screen)
    }

    private var isSetup = false
    fun setupNavigator(navigator: Navigator) {
        if (isSetup) {
            return
        }
        isSetup = true
        logI("setup navigation watcher", tag = "NavigationWatcher")

        viewModelScope.launch {
            pendingScreens.collect { screen ->
                if (!screen.isDeeplinkNavigable()) {
                    return@collect
                }
                logI("navigate $screen", tag = "NavigationWatcher")
                navigator.singleTop(screen)
            }
        }
    }
}
