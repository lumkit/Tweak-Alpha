package io.github.lumkit.tweak.common.utils

expect fun isDebugBuild(): Boolean

expect fun restartApp()

expect val SDK_INT: Int

expect val BOARD: String