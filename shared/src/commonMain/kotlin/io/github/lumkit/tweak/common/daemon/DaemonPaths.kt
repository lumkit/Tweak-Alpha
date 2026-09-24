package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.daemon.DaemonPaths.SERVER_APK_NAME
import io.github.lumkit.tweak.model.RuntimeModeStore
import io.github.lumkit.tweak.model.RuntimeMode

/**
 * TweakServer 工作路径：统一使用 [ConstCommon.Path.TWEAK_ALPHA_ROOT]。
 */
object DaemonPaths {

    const val WORK_ROOT = ConstCommon.Path.TWEAK_ALPHA_ROOT

    const val BATTERY_RECORD_CONF_NAME = "battery_record.conf"
    const val BATTERY_LOGS_DIR_NAME = "battery_logs"
    const val SERVER_PID_NAME = "tweak_server.pid"
    const val WATCHDOG_PID_NAME = "tweak_watchdog.pid"
    /** 存在时表示用户主动停止，双方都不要把对方拉起来。 */
    const val KEEPALIVE_HOLD_NAME = "keepalive.hold"
    const val STARTER_BIN_NAME = "libtweak_starter.so"
    /** 工作区缓存的 App APK 副本（Shizuku 独立 app_process 用） */
    const val SERVER_APK_NAME = "server.apk"
    /** 与 [SERVER_APK_NAME] 对应的版本戳，避免每次冷启整包复制 */
    const val SERVER_APK_STAMP_NAME = "server.apk.stamp"
    const val DEFAULT_BATTERY_LOG_MAX_PART_BYTES = 8L * 1024 * 1024

    data class Resolved(
        val workRoot: String,
        val dir: String,
        val batteryRecordConf: String,
        val batteryLogsDir: String,
        val serverPid: String,
        val watchdogPid: String,
        val keepaliveHold: String,
        val starterBin: String,
        val serverApk: String,
        val serverApkStamp: String,
        /** 目录 chmod，统一 0777 */
        val dirMode: String,
        val runtimeMode: RuntimeMode,
    ) {
        val isRootPrivate: Boolean get() = false
    }

    suspend fun resolve(): Resolved {
        return resolve(RuntimeModeStore.current())
    }

    fun resolve(mode: RuntimeMode): Resolved {
        val workRoot = WORK_ROOT
        val dir = "$workRoot/daemon"
        val dirMode = "0777"
        return Resolved(
            workRoot = workRoot,
            dir = dir,
            batteryRecordConf = "$dir/$BATTERY_RECORD_CONF_NAME",
            batteryLogsDir = "$dir/$BATTERY_LOGS_DIR_NAME",
            serverPid = "$dir/$SERVER_PID_NAME",
            watchdogPid = "$dir/$WATCHDOG_PID_NAME",
            keepaliveHold = "$dir/$KEEPALIVE_HOLD_NAME",
            starterBin = "$dir/$STARTER_BIN_NAME",
            serverApk = "$dir/$SERVER_APK_NAME",
            serverApkStamp = "$dir/$SERVER_APK_STAMP_NAME",
            dirMode = dirMode,
            runtimeMode = mode,
        )
    }
}
