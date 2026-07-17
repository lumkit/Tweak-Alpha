package io.github.lumkit.tweak.adlib.transport

import java.io.Closeable

interface AdbTransport : Closeable {

    val isConnected: Boolean

    fun send(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): Int

    fun receive(
        buffer: ByteArray,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): Int

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Int = 5_000
    }
}
