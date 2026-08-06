package io.github.lumkit.tweak.overlay

import kotlinx.coroutines.flow.StateFlow

expect object OverlayMonitor {
    val loadWatcherIsShowing: StateFlow<Boolean>
    val threadWatcherIsShowing: StateFlow<Boolean>

    fun showLoadWatcherOverlay()
    fun hideLoadWatcherOverlay()

    fun showThreadWatcherOverlay()
    fun hideThreadWatcherOverlay()
}
