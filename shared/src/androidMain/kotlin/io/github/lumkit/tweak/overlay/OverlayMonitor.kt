package io.github.lumkit.tweak.overlay

import android.content.Intent
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.service.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object OverlayMonitor {

    internal val _loadWatcherIsShowing = MutableStateFlow(false)
    internal val _threadWatcherIsShowing = MutableStateFlow(false)
    internal val _miniLoadWatcherIsShowing = MutableStateFlow(false)

    actual val loadWatcherIsShowing: StateFlow<Boolean> = _loadWatcherIsShowing.asStateFlow()
    actual val threadWatcherIsShowing: StateFlow<Boolean> = _threadWatcherIsShowing.asStateFlow()
    actual val miniLoadWatcherIsShowing: StateFlow<Boolean> = _miniLoadWatcherIsShowing.asStateFlow()

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

    actual fun showThreadWatcherOverlay() {
        val intent = Intent(application, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_THREAD_WATCHER_OVERLAY
        }
        application.startService(intent)
    }

    actual fun hideThreadWatcherOverlay() {
        val intent = Intent(application, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE_THREAD_WATCHER_OVERLAY
        }
        application.startService(intent)
    }

    actual fun showMiniLoadWatcherOverlay() {
        val intent = Intent(application, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_MINI_LOAD_WATCHER_OVERLAY
        }
        application.startService(intent)
    }

    actual fun hideMiniLoadWatcherOverlay() {
        val intent = Intent(application, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE_MINI_LOAD_WATCHER_OVERLAY
        }
        application.startService(intent)
    }
}
