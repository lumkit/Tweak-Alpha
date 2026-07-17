package io.github.lumkit.tweak.common

expect object Const {
    object Path {
        val cachePath: String
        val otaPackage: String
        /** 外部存储根目录，默认 `/storage/emulated/0` */
        val externalStorage: String
    }
}

object ConstCommon {
    object Navigation {
        const val ACTION_DEEPLINK_SELF = "ACTION_DEEPLINK_SELF"
    }
}