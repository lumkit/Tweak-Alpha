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

    object Path {
        /** 特权工作区主目录，存放各类功能临时文件 */
        const val TWEAK_ALPHA_ROOT = "/data/local/tmp/TweakAlpha"

        const val LINE_FLASH_DIR = "$TWEAK_ALPHA_ROOT/line_flash"

        fun lineFlashSession(sessionId: String): String = "$LINE_FLASH_DIR/$sessionId"
    }
}