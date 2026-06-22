package io.github.lumkit.tweak.ui.screen.feature.model

import io.github.lumkit.tweak.model.RuntimeMode

enum class Capability {
    ROOT_ONLY,
    SHIZUKU_OR_ROOT,
}

val Capability.runtime: RuntimeMode
    get() = when (this) {
        Capability.ROOT_ONLY -> RuntimeMode.Root
        Capability.SHIZUKU_OR_ROOT -> RuntimeMode.Shizuku
    }
