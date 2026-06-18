package io.github.lumkit.tweak.ui.screen.main

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.ic_category
import tweak_alpha.shared.generated.resources.ic_home
import tweak_alpha.shared.generated.resources.ic_settings
import tweak_alpha.shared.generated.resources.nav_category
import tweak_alpha.shared.generated.resources.nav_home
import tweak_alpha.shared.generated.resources.nav_settings

enum class MainPages {
    Func, Info, Settings
}

val MainPages.iconRes: DrawableResource
    get() = when (this) {
        MainPages.Func -> Res.drawable.ic_category
        MainPages.Info -> Res.drawable.ic_home
        MainPages.Settings -> Res.drawable.ic_settings
    }

val MainPages.titleRes: StringResource
    get() = when (this) {
        MainPages.Func -> Res.string.nav_category
        MainPages.Info -> Res.string.nav_home
        MainPages.Settings -> Res.string.nav_settings
    }
