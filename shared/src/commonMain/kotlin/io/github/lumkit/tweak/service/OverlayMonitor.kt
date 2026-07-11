package io.github.lumkit.tweak.service

import kotlinx.coroutines.flow.StateFlow

expect object OverlayMonitor {
    val loadWatcherIsShowing: StateFlow<Boolean>

    fun showLoadWatcherOverlay()

    fun hideLoadWatcherOverlay()
}
