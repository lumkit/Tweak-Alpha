package io.github.lumkit.tweak.common.shell

import java.util.concurrent.ConcurrentHashMap

actual object ReusableShells {
    private val shells = ConcurrentHashMap<String, ReusableShell>()

    private fun getRuntime(redirectErrorStream: Boolean = false): Process = ShellExecutor.getRuntimeWithRuntimeMode(redirectErrorStream)

    private const val MAX_DEFAULT_PRECESS_SIZE = 8

    @Synchronized
    actual fun getInstance(
        key: String,
        redirectErrorStream: Boolean
    ): ReusableShell {
        val shell = ReusableShell(redirectErrorStream)
        if (!shells.containsKey(key)) {
            shells[key] = shell
        }
        return shell
    }

    actual fun destroyInstance(key: String) {
        if (!shells.containsKey(key)) {
            return
        } else {
            shells[key]?.tryExit()
            shells.remove(key)
        }
    }

    actual fun destroyAll() {
        shells.onEach {
            // 跳过更新引擎进程
            if (it.key != "update_engine_client")
                it.value.tryExit()
        }
        shells.clear()
    }

    private val _defaultReusableShell: ReusableShell
        get() = getDefault("defaultReusableShell")

    private fun getDefault(key: String): ReusableShell {
        val default = shells[key]
        return if (default == null) {
            val shell = ReusableShell()
            shells[key] = shell
            shell
        } else {
            default
        }
    }

    actual val defaultInstance: ReusableShell
        get() {
            var shell = _defaultReusableShell
            for (i in 0 until MAX_DEFAULT_PRECESS_SIZE) {
                val key = "default-$i"
                val process = getDefault(key)
                if (!process.isIdle) {
                    continue
                }
                shell = process
            }
            return shell
        }

    actual fun tryExit() {
        _defaultReusableShell.tryExit()
        for (i in 0 until MAX_DEFAULT_PRECESS_SIZE) {
            val key = "default-$i"
            shells[key]?.tryExit()
        }
    }

    /**
     * 同步执行命令行
     */
    actual suspend fun execSync(vararg cmd: String): String =
        _defaultReusableShell.commitCmdSync(cmd.joinToString("\n"))

    /**
     * 同步执行命令行
     */
    actual suspend fun execSync(cmd: List<String>): String =
        _defaultReusableShell.commitCmdSync(cmd.joinToString("\n"))
}
