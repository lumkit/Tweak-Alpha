package io.github.lumkit.tweak.service

import android.content.Intent
import io.github.lumkit.tweak.application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object OverlayMonitor {

    internal val _loadWatcherIsShowing = MutableStateFlow(false)
    actual val loadWatcherIsShowing: StateFlow<Boolean> = _loadWatcherIsShowing.asStateFlow()

    actual fun showLoadWatcherOverlay() {
        val intent = Intent(application, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_LOAD_WATCHER_OVERLAY
        }
        application.startService(intent)
    }

    actual fun hideLoadWatcherOverlay() {
        val intent = Intent(application, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE_LOAD_WATCHER_OVERLAY
        }
        application.startService(intent)
    }
}
