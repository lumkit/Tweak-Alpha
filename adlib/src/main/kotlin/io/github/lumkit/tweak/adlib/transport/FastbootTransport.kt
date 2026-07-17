package io.github.lumkit.tweak.adlib.transport

import java.io.Closeable

/**
 * Fastboot 底层字节流传输抽象。
 *
 * 该接口位于架构中的：
 * ```
 * FastbootClient
 *        │
 * FastbootTransport   <-- 当前接口
 *        │
 * Android USB Host API
 * ```
 *
 * 它只负责收发原始字节，不理解 Fastboot 协议语义。协议的编解码由
 * [io.github.lumkit.tweak.adlib.client.FastbootClient] 完成。
 */
interface FastbootTransport : Closeable {

    /** 传输通道是否已连接可用。 */
    val isConnected: Boolean

    /**
     * 向设备发送 [length] 字节数据。
     *
     * @param data   待发送缓冲区。
     * @param offset 起始偏移。
     * @param length 发送长度。
     * @param timeoutMillis 超时时间（毫秒）。
     * @return 实际发送的字节数。
     */
    fun send(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): Int

    /**
     * 从设备读取数据到 [buffer]。
     *
     * @param buffer 接收缓冲区。
     * @param timeoutMillis 超时时间（毫秒）。
     * @return 实际读取的字节数。
     */
    fun receive(
        buffer: ByteArray,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): Int

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Int = 5_000
    }
}
