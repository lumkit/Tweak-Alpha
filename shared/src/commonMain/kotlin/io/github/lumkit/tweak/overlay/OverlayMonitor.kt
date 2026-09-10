package io.github.lumkit.tweak.overlay

import kotlinx.coroutines.flow.StateFlow

data class ProcessThreadWatcherTarget(
    val pid: Int,
    val title: String,
)

expect object OverlayMonitor {
    val loadWatcherIsShowing: StateFlow<Boolean>
    val threadWatcherIsShowing: StateFlow<Boolean>
    val miniLoadWatcherIsShowing: StateFlow<Boolean>
    val processThreadWatcherIsShowing: StateFlow<Boolean>
    val processThreadWatcherTarget: StateFlow<ProcessThreadWatcherTarget?>

    fun showLoadWatcherOverlay()
    fun hideLoadWatcherOverlay()

    fun showThreadWatcherOverlay()
    fun hideThreadWatcherOverlay()
    fun showMiniLoadWatcherOverlay()
    fun hideMiniLoadWatcherOverlay()

    fun showProcessThreadWatcherOverlay(pid: Int, title: String)
    fun hideProcessThreadWatcherOverlay()
}
