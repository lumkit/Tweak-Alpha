package io.github.lumkit.tweak.common.base

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.compose.runtime.Composable
import io.github.lumkit.tweak.common.utils.ComposeOverlayHelper
import io.github.lumkit.tweak.common.utils.logD

abstract class BaseOverlayController(
    private val fallbackContextProvider: () -> Context,
    private val accessibilityContextProvider: () -> Context?,
    private val tag: String,
) {

    private var overlayHelper: ComposeOverlayHelper? = null
    private var overlayContext: Context? = null

    protected abstract val overlayName: String

    protected open val startX: Int = 0
    protected open val startY: Int = 0
    protected open val draggable: Boolean = false

    val isShowing: Boolean
        get() = overlayHelper?.isShowing == true

    fun show() {
        val context = resolveOverlayContext()
        val helper = ensureOverlayHelper(context)
        if (helper.isShowing) {
            onShowingChanged(true)
            return
        }

        runCatching {
            helper.show(
                x = startX,
                y = startY,
                draggable = draggable,
            ) {
                Content()
            }
        }.onSuccess {
            val overlayType = if (context is AccessibilityService) {
                "accessibility"
            } else {
                "application"
            }
            onShowingChanged(true)
            logD("show $overlayName success, overlayType=$overlayType", tag)
        }.onFailure { throwable ->
            onShowingChanged(false)
            logD("show $overlayName failed: ${throwable.message}", tag)
        }
    }

    fun hide() {
        overlayHelper?.dismiss()
        onShowingChanged(false)
        logD("hide $overlayName", tag)
    }

    protected open fun onShowingChanged(isShowing: Boolean) = Unit

    @Composable
    protected abstract fun Content()

    protected open fun createOverlayHelper(context: Context): ComposeOverlayHelper {
        return ComposeOverlayHelper(context)
    }

    private fun resolveOverlayContext(): Context {
        return accessibilityContextProvider() ?: fallbackContextProvider()
    }

    private fun ensureOverlayHelper(context: Context): ComposeOverlayHelper {
        val helper = overlayHelper
        if (helper != null && overlayContext === context) {
            return helper
        }

        helper?.dismiss()
        overlayContext = context
        return createOverlayHelper(context).also {
            overlayHelper = it
        }
    }
}
