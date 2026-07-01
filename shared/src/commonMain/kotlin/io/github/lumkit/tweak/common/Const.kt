package io.github.lumkit.tweak.common

expect object Const {
    object Path {
        val cachePath: String
        val otaPackage: String
    }
}

object ConstCommon {
    object Navigation {
        const val ACTION_OPEN_SYSTEM_UPDATE = "ACTION_OPEN_SYSTEM_UPDATE"
    }
}