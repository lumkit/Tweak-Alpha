package io.github.lumkit.tweak.common.utils

data class KernelCommandResult(
    val isSuccess: Boolean,
    val out: List<String>,
    val err: List<String>,
    val code: Int,
)

expect object KernelProps {
    suspend fun getProp(propName: String): String

    suspend fun getSystemProp(propName: String): String

    suspend fun exec(vararg commands: String): KernelCommandResult

    suspend fun exec(commands: List<String>): KernelCommandResult
}
