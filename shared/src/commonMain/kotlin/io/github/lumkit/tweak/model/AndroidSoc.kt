package io.github.lumkit.tweak.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AndroidSoc(
    @SerialName("VENDOR") val vendor: String? = null,
    @SerialName("NAME") val name: String? = null,
    @SerialName("FAB") val fab: String? = null,
    @SerialName("CPU") val cpu: String? = null,
    @SerialName("MEMORY") val memory: String? = null,
    @SerialName("BANDWIDTH") val bandwidth: String? = null,
    @SerialName("CHANNELS") val channels: String? = null,
) {

    override fun toString(): String = buildString {
        append("处理器厂商: ")
        append(vendor)
        append("\n")
        append("制程工艺: ")
        append(fab)
        if (!cpu.isNullOrEmpty()) {
            append("\n")
            append("CPU: ")
            append(cpu)
        }
        if (!memory.isNullOrEmpty()) {
            append("\n")
            append("内存: ")
            append(memory)
        }
        if (!channels.isNullOrEmpty()) {
            append("\n")
            append("通道: ")
            append(channels)
        }
        if (!bandwidth.isNullOrEmpty()) {
            append("\n")
            append("Bandwidth: ")
            append(bandwidth)
        }
    }

}