package io.github.lumkit.tweak.common.base

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import io.github.lumkit.tweak.common.utils.ComposeOverlayHelper
import io.github.lumkit.tweak.common.utils.logD

abstract class BaseOverlayController(
    private val contextProvider: () -> Context,
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

    fun show(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.START or Gravity.TOP,
        x: Int = startX,
        y: Int = startY,
        draggable: Boolean = true,
    ) {
        val context = resolveOverlayContext()
        val helper = ensureOverlayHelper(context)
        if (helper.isShowing) {
            onShowingChanged(true)
            return
        }

        runCatching {
            helper.show(
                width = width,
                height = height,
                gravity = gravity,
                x = x,
                y = y,
                draggable = draggable,
            ) {
                Content()
            }
        }.onSuccess {
            onShowingChanged(true)
            logD("show $overlayName success", tag)
        }.onFailure { throwable ->
            onShowingChanged(false)
            logD("show $overlayName failed: ${throwable.message}", tag)
        }
    }

    fun hide() {
        overlayHelper?.getPosition()?.let { (x, y) ->
            onPositionShouldPersist(x, y)
        }
        overlayHelper?.dismiss()
        onShowingChanged(false)
        logD("hide $overlayName", tag)
    }

    protected open fun onShowingChanged(isShowing: Boolean) = Unit

    protected open fun onPositionShouldPersist(x: Int, y: Int) = Unit

    @Composable
    protected abstract fun Content()

    protected open fun createOverlayHelper(context: Context): ComposeOverlayHelper {
        return ComposeOverlayHelper(context)
    }

    private fun resolveOverlayContext(): Context = contextProvider()

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
