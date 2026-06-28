package io.github.lumkit.tweak.common.shell

/**
 * 可复用 Shell 管理器。
 */
expect object ReusableShells {
    fun getInstance(key: String, redirectErrorStream: Boolean = false): ReusableShell

    fun destroyInstance(key: String)

    fun destroyAll()

    val defaultInstance: ReusableShell

    fun tryExit()

    suspend fun execSync(vararg cmd: String): String

    suspend fun execSync(cmd: List<String>): String
}
