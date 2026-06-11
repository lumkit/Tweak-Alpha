package io.github.lumkit.tweak.common.component

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import io.github.lumkit.tweak.application

internal fun getCornerRadius(context: Context): Int {
    val resources = context.resources

    val names = listOf(
        "rounded_corner_radius",
        "rounded_corner_radius_top",
        "rounded_corner_radius_bottom"
    )

    for (name in names) {
        val id = resources.getIdentifier(name, "dimen", "android")
        if (id > 0) {
            return resources.getDimensionPixelSize(id)
        }
    }

    return 0
}

internal fun getScreenCornerRadius(view: View): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return view.rootWindowInsets
            ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            ?.radius ?: 0
    }

    return getCornerRadius(application)
}

@Composable
actual fun rememberScreenCornerRadius(): Int = getScreenCornerRadius(LocalView.current)