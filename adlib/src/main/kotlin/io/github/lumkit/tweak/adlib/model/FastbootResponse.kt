package io.github.lumkit.tweak.adlib.model

/**
 * 表示一次 Fastboot 命令的响应结果。
 *
 * Fastboot 协议中，设备对每条命令会回复若干条以四字节前缀开头的消息：
 * - `OKAY`：命令成功，后续为可选的返回值。
 * - `FAIL`：命令失败，后续为失败原因。
 * - `DATA`：设备准备接收数据，后续为十六进制数据长度。
 * - `INFO`：设备的中间信息（进度、日志等），可能出现多条。
 */
sealed interface FastbootResponse {

    /** 命令执行过程中设备回复的所有 INFO 信息。 */
    val infos: List<String>

    /** 命令成功。 */
    data class Okay(
        val payload: String,
        override val infos: List<String>,
    ) : FastbootResponse

    /** 命令失败。 */
    data class Fail(
        val reason: String,
        override val infos: List<String>,
    ) : FastbootResponse

    /** 设备已就绪，等待接收 [size] 字节的数据。 */
    data class Data(
        val size: Long,
        override val infos: List<String>,
    ) : FastbootResponse
}
