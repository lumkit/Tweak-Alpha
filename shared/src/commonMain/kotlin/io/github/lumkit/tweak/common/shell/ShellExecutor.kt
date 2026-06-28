package io.github.lumkit.tweak.common.shell

import java.io.IOException

expect object ShellExecutor {
    @Throws(exceptionClasses = [IOException::class])
    fun resolveSuperUserId(): String

    suspend fun getRuntimeWithRuntimeMode(redirectErrorStream: Boolean = false): Process
}