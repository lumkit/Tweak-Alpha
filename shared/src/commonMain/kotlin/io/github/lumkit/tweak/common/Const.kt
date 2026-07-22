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
        const val ACTION_DEEPLINK_SELF = "io.github.lumkit.tweak.ACTION_DEEPLINK_SELF"
        const val EXTRA_NAV_INTENT = "nav_intent"
        /** adb 简易跳转：`--es route ProcessManager` */
        const val EXTRA_ROUTE = "route"
        const val EXTRA_SCROLL_TO_PACKAGE = "scrollToPackage"
        const val EXTRA_SCROLL_TO_PID = "scrollToPid"
        const val EXTRA_ID = "id"
    }

    object Path {
        /** 特权工作区主目录，存放各类功能临时文件 */
        const val TWEAK_ALPHA_ROOT = "/data/local/tmp/TweakAlpha"

        const val LINE_FLASH_DIR = "$TWEAK_ALPHA_ROOT/line_flash"

        fun lineFlashSession(sessionId: String): String = "$LINE_FLASH_DIR/$sessionId"

        /** 特权 native daemon 工作区 */
        const val DAEMON_DIR = "$TWEAK_ALPHA_ROOT/daemon"
        const val DAEMON_BIN = "$DAEMON_DIR/tweakd"
        const val DAEMON_PID = "$DAEMON_DIR/tweakd.pid"
        const val DAEMON_SOCK = "$DAEMON_DIR/tweakd.sock"
        const val DAEMON_LOG = "$DAEMON_DIR/tweakd.log"
    }
}