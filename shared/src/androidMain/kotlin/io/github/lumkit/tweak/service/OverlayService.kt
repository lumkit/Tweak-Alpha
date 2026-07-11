package io.github.lumkit.tweak.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class OverlayService : Service() {

    companion object {
        const val TAG = "LoadWatcherOverlayService"
        const val ACTION_SHOW_LOAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.SHOW_LOAD_WATCHER_OVERLAY"
        const val ACTION_HIDE_LOAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.HIDE_LOAD_WATCHER_OVERLAY"
    }

    private val loadWatcherOverlayController by lazy {
        LoadWatcherOverlayController(
            fallbackContextProvider = { this },
            accessibilityContextProvider = { TweakAccessibilityService.overlayContextOrNull },
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_LOAD_WATCHER_OVERLAY -> loadWatcherOverlayController.show()
            ACTION_HIDE_LOAD_WATCHER_OVERLAY -> {
                loadWatcherOverlayController.hide()
                stopIfNoOverlayShowing()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        loadWatcherOverlayController.hide()
        super.onDestroy()
    }

    private fun stopIfNoOverlayShowing() {
        if (!loadWatcherOverlayController.isShowing) {
            stopSelf()
        }
    }
}
