package io.github.lumkit.tweak.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.Gravity
import io.github.lumkit.tweak.overlay.LoadWatcherOverlayController
import io.github.lumkit.tweak.overlay.MiniLoadWatcherOverlayController
import io.github.lumkit.tweak.overlay.ThreadWatcherOverlayController

class OverlayService : Service() {

    companion object {
        const val TAG = "OverlayService"
        const val ACTION_SHOW_LOAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.SHOW_LOAD_WATCHER_OVERLAY"
        const val ACTION_HIDE_LOAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.HIDE_LOAD_WATCHER_OVERLAY"

        const val ACTION_SHOW_THREAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.SHOW_THREAD_WATCHER_OVERLAY"
        const val ACTION_HIDE_THREAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.HIDE_THREAD_WATCHER_OVERLAY"

        const val ACTION_SHOW_MINI_LOAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.SHOW_MINI_LOAD_WATCHER_OVERLAY"
        const val ACTION_HIDE_MINI_LOAD_WATCHER_OVERLAY =
            "io.github.lumkit.tweak.action.HIDE_MINI_LOAD_WATCHER_OVERLAY"
    }

    private val loadWatcherOverlayController by lazy {
        LoadWatcherOverlayController(
            fallbackContextProvider = { this },
            accessibilityContextProvider = { TweakAccessibilityService.overlayContextOrNull },
        )
    }

    private val threadWatcherOverlayController by lazy {
        ThreadWatcherOverlayController(
            fallbackContextProvider = { this },
            accessibilityContextProvider = { TweakAccessibilityService.overlayContextOrNull },
        )
    }

    private val miniLoadWatcherOverlayController by lazy {
        MiniLoadWatcherOverlayController(
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

            ACTION_SHOW_THREAD_WATCHER_OVERLAY -> threadWatcherOverlayController.show()
            ACTION_HIDE_THREAD_WATCHER_OVERLAY -> {
                threadWatcherOverlayController.hide()
                stopIfNoOverlayShowing()
            }

            ACTION_SHOW_MINI_LOAD_WATCHER_OVERLAY -> miniLoadWatcherOverlayController.show(
                x = 0,
                y = 0,
                gravity = Gravity.CENTER or Gravity.TOP,
                draggable = false,
            )
            ACTION_HIDE_MINI_LOAD_WATCHER_OVERLAY -> {
                miniLoadWatcherOverlayController.hide()
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
        val stateList = listOf(
            loadWatcherOverlayController.isShowing,
            threadWatcherOverlayController.isShowing,
            miniLoadWatcherOverlayController.isShowing,
        )
        if (stateList.all { !it }) {
            stopSelf()
        }
    }
}
