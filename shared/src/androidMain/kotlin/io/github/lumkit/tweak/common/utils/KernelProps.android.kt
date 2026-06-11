package io.github.lumkit.tweak.common.utils

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object KernelProps {
    actual suspend fun getProp(propName: String): String {
        val command = """if [[ -e "$propName" ]]; then cat "$propName"; fi;"""
        return execute(command)
    }

    actual suspend fun getSystemProp(propName: String): String {
        return execute("""getprop "$propName"""")
    }

    actual suspend fun exec(vararg commands: String): KernelCommandResult {
        return exec(commands.asList())
    }

    actual suspend fun exec(commands: List<String>): KernelCommandResult {
        return withContext(Dispatchers.IO) {
            Shell.getShell()
            Shell.cmd(*commands.toTypedArray()).exec().let { result ->
                KernelCommandResult(
                    isSuccess = result.isSuccess,
                    out = result.out,
                    err = result.err,
                    code = result.code,
                )
            }
        }
    }

    private suspend fun execute(command: String): String {
        val result = exec(command)
        if (!result.isSuccess) {
            return ""
        }
        return result.out.joinToString(separator = "\n").trim()
    }
}
