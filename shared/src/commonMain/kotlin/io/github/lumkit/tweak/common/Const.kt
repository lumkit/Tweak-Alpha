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
    /** 无 applicationIdSuffix 时的默认包名；dev 为 `.alpha` 后缀，由 starter 传入 */
    const val APP_PACKAGE_DEFAULT = "io.github.lumkit.tweak"

    object Navigation {
        const val ACTION_DEEPLINK_SELF = "io.github.lumkit.tweak.ACTION_DEEPLINK_SELF"
        const val EXTRA_NAV_INTENT = "nav_intent"
        /** adb 简易跳转：`--es route ProcessManager` */
        const val EXTRA_ROUTE = "route"
        const val EXTRA_SCROLL_TO_PACKAGE = "scrollToPackage"
        const val EXTRA_SCROLL_TO_PID = "scrollToPid"
        const val EXTRA_ID = "id"
        /**
         * 内部崩溃通道：CrashReport JSON。
         * 仅 UncaughtExceptionHandler 写入，不走 adb route / 通用 nav_intent，且不落盘。
         */
        const val EXTRA_CRASH_REPORT = "crash_report"
    }

    object Path {
        /** Shizuku/ADB 共享工作区（line_flash 等仍用此根）。Daemon 路径见 [io.github.lumkit.tweak.common.daemon.DaemonPaths]。 */
        const val TWEAK_ALPHA_ROOT = "/data/local/tmp/tweak-alpha"

        const val LINE_FLASH_DIR = "$TWEAK_ALPHA_ROOT/line_flash"

        fun lineFlashSession(sessionId: String): String = "$LINE_FLASH_DIR/$sessionId"
    }

    object Accessibility {
        const val SERVICE_CLASS = "io.github.lumkit.tweak.service.TweakAccessibilityService"
    }
}