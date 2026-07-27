package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Native daemon 工作路径：按 [RuntimeMode] 分流。
 * - Root → `/data/adb/tweak-alpha`（私有，0700）
 * - Shizuku → `/data/local/tmp/tweak-alpha`（ADB/shell 工作区）
 */
object DaemonPaths {

    const val ROOT_WORK_ROOT = "/data/adb/tweak-alpha"
    const val SHIZUKU_WORK_ROOT = "/data/local/tmp/tweak-alpha"

    const val A11Y_WATCH_CONF_NAME = "a11y_watch.conf"
    const val DEFAULT_A11Y_INTERVAL_MS = 60_000
    const val MIN_A11Y_INTERVAL_MS = 10_000

    data class Resolved(
        val workRoot: String,
        val dir: String,
        val bin: String,
        val pid: String,
        val port: String,
        val log: String,
        val a11yConf: String,
        /** 目录 chmod，Root 用 0700，Shizuku 用 0777 */
        val dirMode: String,
        val runtimeMode: RuntimeMode,
    ) {
        val isRootPrivate: Boolean get() = runtimeMode == RuntimeMode.Root
    }

    suspend fun resolve(): Resolved {
        val mode = GlobalViewModel.runtimeModeState.filterNotNull().first()
        return resolve(mode)
    }

    fun resolve(mode: RuntimeMode): Resolved {
        val workRoot = when (mode) {
            RuntimeMode.Root -> ROOT_WORK_ROOT
            RuntimeMode.Shizuku, RuntimeMode.Unknow -> SHIZUKU_WORK_ROOT
        }
        val dir = "$workRoot/daemon"
        val dirMode = when (mode) {
            RuntimeMode.Root -> "0700"
            RuntimeMode.Shizuku, RuntimeMode.Unknow -> "0777"
        }
        return Resolved(
            workRoot = workRoot,
            dir = dir,
            bin = "$dir/tweakd",
            pid = "$dir/tweakd.pid",
            port = "$dir/tweakd.port",
            log = "$dir/tweakd.log",
            a11yConf = "$dir/$A11Y_WATCH_CONF_NAME",
            dirMode = dirMode,
            runtimeMode = mode,
        )
    }
}
