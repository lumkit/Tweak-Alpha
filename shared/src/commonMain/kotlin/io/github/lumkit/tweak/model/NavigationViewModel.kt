package io.github.lumkit.tweak.model

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.logI
import io.github.lumkit.tweak.navigation.Navigator
import io.github.lumkit.tweak.navigation.Screen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object NavigationViewModel: BaseViewModel() {

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    fun navigate(intent: NavigationIntent) = suspendLaunch(
        id = "navigate",
    ) {
        loading()
        success(json.encodeToString(intent))
    }

    private var isSetup = false
    fun setupNavigator(navigator: Navigator) {
        if (isSetup) {
            return
        }
        isSetup = true
        logI("setup navigation watcher", tag = "NavigationWatcher")

        val id = "navigate"
        viewModelScope.launch {
            loadState.collect {
                logI("watch $id, map = $it", tag = "NavigationWatcher")
                val state = it[id] ?: return@collect
                logI("watch $id, state = $state", tag = "NavigationWatcher")
                launch {
                    navHandle(state, navigator)
                }
                clearLoadState(id)
            }
        }
    }

    private fun navHandle(loadState: LoadState, navigator: Navigator) {
        when (loadState) {
            is LoadState.Failure -> Unit
            is LoadState.Loading -> Unit
            is LoadState.Success -> {
                val intent = runCatching {
                    json.decodeFromString<NavigationIntent>(loadState.message ?: "{}")
                }.getOrNull() ?: return

                // 跳转导航
                when (intent.targetScreen) {

                    NavigationIntentTargetScreen.UpdateSystem -> {
                        val route = json.decodeFromString<Screen.UpdateSystem>(intent.screenJson)
                        navigator.singleTop(route)
                    }

                    NavigationIntentTargetScreen.FpsRecord -> {
                        val route = json.decodeFromString<Screen.FpsRecord>(intent.screenJson)
                        navigator.singleTop(route)
                    }
                }
            }
        }
    }
}