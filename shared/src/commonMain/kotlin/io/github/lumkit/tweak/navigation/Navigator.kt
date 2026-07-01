package io.github.lumkit.tweak.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("not provided.") }

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(val state: NavigationState){
    fun navigate(route: NavKey, cleanTask: Boolean = false){
        if (route in state.backStacks.keys){
            // This is a top level route, just switch to it.
            state.topLevelRoute = route
        } else {
            val navKeys = state.backStacks[state.topLevelRoute]
            navKeys?.also {
                if (cleanTask) {
                    it.clear()
                }
                it.add(route)
            }
        }
    }

    fun singleTopNavigate(route: NavKey){
        if (route in state.backStacks.keys){
            // This is a top level route, just switch to it.
            state.topLevelRoute = route
        } else {
            val navKeys = state.backStacks[state.topLevelRoute]
            navKeys?.also {
                it.removeIf { it::class == route::class }
                it.add(route)
            }
        }
    }

    fun goBack(){
        val currentStack = state.backStacks[state.topLevelRoute] ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // If we're at the base of the current route, go back to the start route stack.
        if (currentRoute == state.topLevelRoute){
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}