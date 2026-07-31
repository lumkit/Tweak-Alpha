package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * TweakServer 工作路径：统一使用 [ConstCommon.Path.TWEAK_ALPHA_ROOT]。
 */
object DaemonPaths {

    const val WORK_ROOT = ConstCommon.Path.TWEAK_ALPHA_ROOT

    const val A11Y_WATCH_CONF_NAME = "a11y_watch.conf"
    const val BATTERY_RECORD_CONF_NAME = "battery_record.conf"
    const val BATTERY_LOGS_DIR_NAME = "battery_logs"
    const val SERVER_PID_NAME = "tweak_server.pid"
    const val STARTER_BIN_NAME = "libtweak_starter.so"
    /** 工作区缓存的 App APK 副本（Shizuku 独立 app_process 用） */
    const val SERVER_APK_NAME = "server.apk"
    /** 与 [SERVER_APK_NAME] 对应的版本戳，避免每次冷启整包复制 */
    const val SERVER_APK_STAMP_NAME = "server.apk.stamp"
    const val DEFAULT_BATTERY_LOG_MAX_PART_BYTES = 8L * 1024 * 1024
    const val DEFAULT_A11Y_INTERVAL_MS = 60_000
    const val MIN_A11Y_INTERVAL_MS = 10_000

    data class Resolved(
        val workRoot: String,
        val dir: String,
        val a11yConf: String,
        val batteryRecordConf: String,
        val batteryLogsDir: String,
        val serverPid: String,
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
        val mode = GlobalViewModel.runtimeModeState.value
            ?: withTimeoutOrNull(5.seconds) {
                GlobalViewModel.runtimeModeState.filterNotNull().first()
            }
            ?: TweakDataStore.runtimeModeFlow().first()
        return resolve(mode)
    }

    fun resolve(mode: RuntimeMode): Resolved {
        val workRoot = WORK_ROOT
        val dir = "$workRoot/daemon"
        val dirMode = "0777"
        return Resolved(
            workRoot = workRoot,
            dir = dir,
            a11yConf = "$dir/$A11Y_WATCH_CONF_NAME",
            batteryRecordConf = "$dir/$BATTERY_RECORD_CONF_NAME",
            batteryLogsDir = "$dir/$BATTERY_LOGS_DIR_NAME",
            serverPid = "$dir/$SERVER_PID_NAME",
            starterBin = "$dir/$STARTER_BIN_NAME",
            serverApk = "$dir/$SERVER_APK_NAME",
            serverApkStamp = "$dir/$SERVER_APK_STAMP_NAME",
            dirMode = dirMode,
            runtimeMode = mode,
        )
    }
}
