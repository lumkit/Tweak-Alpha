package io.github.lumkit.tweak.model

/**
 * 进程内待展示的崩溃报告。
 * 由崩溃 Intent extras 载入，不落盘。
 */
object CrashSession {
    @Volatile
    private var pending: CrashReport? = null

    fun hasPending(): Boolean = pending != null

    fun peek(): CrashReport? = pending

    fun setPending(report: CrashReport) {
        pending = report
    }

    fun clear() {
        pending = null
    }
}
