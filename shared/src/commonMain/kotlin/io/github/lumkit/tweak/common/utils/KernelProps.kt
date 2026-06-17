package io.github.lumkit.tweak.common.utils

expect object KernelProps {
    suspend fun getProp(propName: String): String

    suspend fun getSystemProp(propName: String): String
}
