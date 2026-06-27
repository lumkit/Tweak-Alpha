package io.github.lumkit.tweak.common.shell

import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import java.util.concurrent.ConcurrentHashMap

actual object ReusableShells {
    private val shells = ConcurrentHashMap<String, KeepShell>()

    private fun getRuntime(redirectErrorStream: Boolean = false): Process {
        return when (GlobalViewModel.runtimeModeState.value) {
            RuntimeMode.Unknow -> error("this should not happen, please report this to the developer")
            RuntimeMode.Root -> ShellExecutor.getSuperUserRuntime(redirectErrorStream)
            RuntimeMode.Shizuku -> ShellExecutor.getShizukuRuntime()
            null -> error("this should not happen, please report this to the developer")
        }
    }

    private const val MAX_DEFAULT_PRECESS_SIZE = 8

    @Synchronized
    actual fun getInstance(
        key: String,
        redirectErrorStream: Boolean
    ): KeepShell {
        val shell = KeepShell().apply {
            setRuntime(this@ReusableShells.getRuntime())
        }
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

    private val _defaultReusableShell: KeepShell
        get() = getDefault("defaultReusableShell")

    private fun getDefault(key: String): KeepShell {
        val default = shells[key]
        return if (default == null) {
            val shell = KeepShell().apply {
                setRuntime(this@ReusableShells.getRuntime())
            }
            shells[key] = shell
            shell
        } else {
            default
        }
    }

    actual val defaultInstance: KeepShell
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
        _defaultReusableShell.doCmdSync(cmd.joinToString("\n"))

    /**
     * 同步执行命令行
     */
    actual suspend fun execSync(cmd: List<String>): String =
        _defaultReusableShell.doCmdSync(cmd.joinToString("\n"))
}
