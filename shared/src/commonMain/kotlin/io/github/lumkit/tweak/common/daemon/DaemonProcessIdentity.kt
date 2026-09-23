package io.github.lumkit.tweak.common.daemon

/**
 * 用进程 cmdline 判断 pidfile 里的 pid 是不是仍是 `tweak_server`。
 *
 * `/proc/<pid>` 存在不够：进程退出后 pid 会被系统复用。读不到 cmdline 时必须视为无法确认，
 * 不能把任意存活进程当成采集进程。
 */
object DaemonProcessIdentity {
    fun isTweakServerCmdline(cmdline: String?): Boolean {
        if (cmdline.isNullOrBlank()) return false
        return cmdline.contains("tweak_server")
    }
}
