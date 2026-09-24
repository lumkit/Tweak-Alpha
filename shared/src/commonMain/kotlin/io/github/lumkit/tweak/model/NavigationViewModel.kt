package io.github.lumkit.tweak.model

import io.github.lumkit.tweak.common.utils.logI
import io.github.lumkit.tweak.navigation.Navigator
import io.github.lumkit.tweak.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * 深链在 Activity 收到时导航器可能还没挂上，所以先记在进程里，等主界面订阅后再跳。
 */
object NavigationViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

        scope.launch {
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
