package io.github.lumkit.tweak.common.shell

import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import java.util.concurrent.ConcurrentHashMap

object ReusableShells {
    private val shells = ConcurrentHashMap<String, KeepShell>()

    private fun getRuntime(): Process {
        return when (GlobalViewModel.runtimeModeState.value) {
            RuntimeMode.Unknow -> error("this should not happen, please report this to the developer")
            RuntimeMode.Root -> ShellExecutor.getSuperUserRuntime()
            RuntimeMode.Shizuku -> ShellExecutor.getShizukuRuntime()
            null -> error("this should not happen, please report this to the developer")
        }
    }

    private const val MAX_DEFAULT_PRECESS_SIZE = 8

    @Synchronized
    fun getInstance(
        key: String
    ): KeepShell {
        val shell = KeepShell().apply {
            setRuntime(this@ReusableShells.getRuntime())
        }
        if (!shells.containsKey(key)) {
            shells[key] = shell
        }
        return shell
    }

    fun destroyInstance(key: String) {
        if (!shells.containsKey(key)) {
            return
        } else {
            shells[key]?.tryExit()
            shells.remove(key)
        }
    }

    fun destroyAll() {
        shells.onEach {
            // 跳过更新引擎进程
            if (it.key != "update_engine_client")
                it.value.tryExit()
        }
        shells.clear()
    }

    private val defaultReusableShell: KeepShell
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

    val getDefaultInstance: KeepShell
        get() {
            var shell = defaultReusableShell
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

    fun tryExit() {
        defaultReusableShell.tryExit()
        for (i in 0 until MAX_DEFAULT_PRECESS_SIZE) {
            val key = "default-$i"
            shells[key]?.tryExit()
        }
    }

    /**
     * 同步执行命令行
     */
    suspend fun execSync(vararg cmd: String): String =
        defaultReusableShell.doCmdSync(cmd.joinToString("\n"))

    /**
     * 同步执行命令行
     */
    suspend fun execSync(cmd: List<String>): String =
        defaultReusableShell.doCmdSync(cmd.joinToString("\n"))
}