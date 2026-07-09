package io.github.lumkit.tweak.common

expect object Const {
    object Path {
        val cachePath: String
        val otaPackage: String
    }
}

object ConstCommon {
    object Navigation {
        const val ACTION_DEEPLINK_SELF = "ACTION_DEEPLINK_SELF"
    }
}