package io.github.lumkit.tweak.adlib.client

import io.github.lumkit.tweak.adlib.exception.AdbProtocolException
import io.github.lumkit.tweak.adlib.exception.AdbSideloadException
import io.github.lumkit.tweak.adlib.transport.AdbTransport
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class OtaAdbClient(
    private val transport: AdbTransport,
) : Closeable {

    private var localId: Int = DEFAULT_LOCAL_ID
    private var remoteId: Int = 0
    private var maxPayload: Int = DEFAULT_MAX_PAYLOAD
    private var connected: Boolean = false

    fun connect(systemIdentity: String = DEFAULT_SYSTEM_IDENTITY) {
        sendPacket(
            command = CMD_CNXN,
            arg0 = A_VERSION,
            arg1 = DEFAULT_MAX_PAYLOAD,
            payload = systemIdentity.toByteArray(Charsets.UTF_8),
        )

        while (true) {
            when (val packet = readPacket(HANDSHAKE_TIMEOUT_MILLIS)) {
                is Packet.Cnxn -> {
                    maxPayload = packet.arg1.coerceAtLeast(MIN_PAYLOAD)
                    connected = true
                    return
                }
                is Packet.Auth -> {
                    throw AdbProtocolException(
                        "Device requires ADB authentication. Recovery sideload usually expects unauthenticated host access.",
                    )
                }
                else -> Unit
            }
        }
    }

    fun sideload(
        otaPackage: ByteArray,
        blockSize: Int = DEFAULT_SIDELOAD_BLOCK_SIZE,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ) {
        ensureConnected()
        require(blockSize > 0) { "blockSize must be > 0" }

        val blockCount = (otaPackage.size + blockSize - 1) / blockSize
        val destination = "sideload-host:$blockCount:$blockSize"
        open(destination)

        try {
            var finished = false
            while (!finished) {
                when (val packet = readServicePacket(SIDELOAD_TIMEOUT_MILLIS)) {
                    is Packet.Write -> {
                        val request = packet.payloadText().trim()
                        val blockIndex = request.toIntOrNull()
                            ?: throw AdbProtocolException("Unexpected sideload request: $request")
                        val block = otaPackage.sliceBlock(blockIndex, blockSize)
                        sendOkay(packet.arg1, packet.arg0)
                        sendWrite(packet.arg1, block)
                        val offset = (blockIndex.toLong() + 1L) * blockSize.toLong()
                        onProgress?.invoke(min(offset, otaPackage.size.toLong()), otaPackage.size.toLong())
                    }

                    is Packet.Okay -> {
                        remoteId = packet.arg0
                    }

                    is Packet.Close -> {
                        sendClose(packet.arg1, packet.arg0)
                        finished = true
                    }

                    else -> throw AdbProtocolException("Unexpected packet during sideload: ${packet.commandName}")
                }
            }
        } finally {
            if (remoteId != 0) {
                runCatching { sendClose(localId, remoteId) }
            }
            remoteId = 0
        }
    }

    private fun open(destination: String) {
        val payload = destination.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        sendPacket(CMD_OPEN, localId, 0, payload)

        while (true) {
            when (val packet = readPacket(HANDSHAKE_TIMEOUT_MILLIS)) {
                is Packet.Okay -> {
                    remoteId = packet.arg0
                    return
                }

                is Packet.Close -> {
                    throw AdbSideloadException("Recovery rejected sideload service: ${packet.payloadText()}")
                }

                is Packet.Write -> {
                    sendOkay(packet.arg1, packet.arg0)
                }

                else -> Unit
            }
        }
    }

    private fun readServicePacket(timeoutMillis: Int): Packet {
        while (true) {
            when (val packet = readPacket(timeoutMillis)) {
                is Packet.Write -> {
                    if (packet.arg0 != remoteId || packet.arg1 != localId) {
                        throw AdbProtocolException("Received WRITE for unexpected stream")
                    }
                    return packet
                }

                is Packet.Okay -> {
                    if (packet.arg0 == remoteId && packet.arg1 == localId) {
                        return packet
                    }
                }

                is Packet.Close -> {
                    if (packet.arg0 == remoteId && packet.arg1 == localId) {
                        return packet
                    }
                }

                else -> Unit
            }
        }
    }

    private fun sendWrite(remoteId: Int, payload: ByteArray) {
        sendPacket(CMD_WRTE, localId, remoteId, payload)
        val ack = readPacket(SIDELOAD_TIMEOUT_MILLIS)
        if (ack !is Packet.Okay || ack.arg0 != remoteId || ack.arg1 != localId) {
            throw AdbProtocolException("Expected OKAY after WRTE, got ${ack.commandName}")
        }
    }

    private fun sendOkay(localId: Int, remoteId: Int) {
        sendPacket(CMD_OKAY, localId, remoteId, EMPTY_BYTES)
    }

    private fun sendClose(localId: Int, remoteId: Int) {
        sendPacket(CMD_CLSE, localId, remoteId, EMPTY_BYTES)
    }

    private fun sendPacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        if (payload.size > maxPayload && command == CMD_WRTE) {
            throw AdbProtocolException("Payload ${payload.size} exceeds device max payload $maxPayload")
        }
        val header = ByteBuffer.allocate(ADB_HEADER_LENGTH)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(payload.size)
            .putInt(payload.sum32())
            .putInt(command xor -0x1)
            .array()
        transport.send(header)
        if (payload.isNotEmpty()) {
            var offset = 0
            while (offset < payload.size) {
                val chunk = min(maxPayload, payload.size - offset)
                val written = transport.send(payload, offset, chunk, SIDELOAD_TIMEOUT_MILLIS)
                if (written <= 0) {
                    throw AdbProtocolException("ADB payload write stalled")
                }
                offset += written
            }
        }
    }

    private fun readPacket(timeoutMillis: Int): Packet {
        val headerBuffer = ByteArray(ADB_HEADER_LENGTH)
        readFully(headerBuffer, timeoutMillis)
        val header = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int
        val arg0 = header.int
        val arg1 = header.int
        val dataLength = header.int
        val dataCheck = header.int
        val magic = header.int

        if (magic != (command xor -0x1)) {
            throw AdbProtocolException("Invalid ADB packet magic")
        }
        if (dataLength < 0 || dataLength > MAX_PACKET_SIZE) {
            throw AdbProtocolException("Invalid ADB packet length: $dataLength")
        }

        val payload = ByteArray(dataLength)
        if (dataLength > 0) {
            readFully(payload, timeoutMillis)
            if (payload.sum32() != dataCheck) {
                throw AdbProtocolException("ADB payload checksum mismatch")
            }
        }

        return when (command) {
            CMD_CNXN -> Packet.Cnxn(arg0, arg1, payload)
            CMD_AUTH -> Packet.Auth(arg0, arg1, payload)
            CMD_OPEN -> Packet.Open(arg0, arg1, payload)
            CMD_OKAY -> Packet.Okay(arg0, arg1, payload)
            CMD_CLSE -> Packet.Close(arg0, arg1, payload)
            CMD_WRTE -> Packet.Write(arg0, arg1, payload)
            else -> Packet.Unknown(command, arg0, arg1, payload)
        }
    }

    private fun readFully(buffer: ByteArray, timeoutMillis: Int) {
        var offset = 0
        val chunkBuffer = ByteArray(buffer.size)
        while (offset < buffer.size) {
            val count = transport.receive(chunkBuffer, timeoutMillis)
            if (count <= 0) {
                throw AdbProtocolException("ADB read stalled")
            }
            if (offset + count > buffer.size) {
                throw AdbProtocolException("ADB packet overflow")
            }
            System.arraycopy(chunkBuffer, 0, buffer, offset, count)
            offset += count
        }
    }

    private fun ensureConnected() {
        if (!connected) {
            throw AdbProtocolException("ADB client is not connected")
        }
    }

    override fun close() {
        transport.close()
    }

    private sealed class Packet(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray,
    ) {
        val commandName: String
            get() = command.toAdbCommandName()

        fun payloadText(): String = payload.toString(Charsets.UTF_8)

        class Cnxn(arg0: Int, arg1: Int, payload: ByteArray) : Packet(CMD_CNXN, arg0, arg1, payload)
        class Auth(arg0: Int, arg1: Int, payload: ByteArray) : Packet(CMD_AUTH, arg0, arg1, payload)
        class Open(arg0: Int, arg1: Int, payload: ByteArray) : Packet(CMD_OPEN, arg0, arg1, payload)
        class Okay(arg0: Int, arg1: Int, payload: ByteArray) : Packet(CMD_OKAY, arg0, arg1, payload)
        class Close(arg0: Int, arg1: Int, payload: ByteArray) : Packet(CMD_CLSE, arg0, arg1, payload)
        class Write(arg0: Int, arg1: Int, payload: ByteArray) : Packet(CMD_WRTE, arg0, arg1, payload)
        class Unknown(command: Int, arg0: Int, arg1: Int, payload: ByteArray) : Packet(command, arg0, arg1, payload)
    }

    companion object {
        private const val ADB_HEADER_LENGTH = 24
        private const val A_VERSION = 0x01000000
        private const val DEFAULT_LOCAL_ID = 1
        private const val DEFAULT_MAX_PAYLOAD = 256 * 1024
        private const val MIN_PAYLOAD = 4096
        private const val MAX_PACKET_SIZE = 1024 * 1024
        private const val HANDSHAKE_TIMEOUT_MILLIS = 10_000
        private const val SIDELOAD_TIMEOUT_MILLIS = 30_000
        private const val DEFAULT_SIDELOAD_BLOCK_SIZE = 64 * 1024
        private const val DEFAULT_SYSTEM_IDENTITY = "host::tweak-adlib"

        private const val CMD_CNXN = 0x4e584e43
        private const val CMD_OPEN = 0x4e45504f
        private const val CMD_OKAY = 0x59414b4f
        private const val CMD_CLSE = 0x45534c43
        private const val CMD_WRTE = 0x45545257
        private const val CMD_AUTH = 0x48545541

        private val EMPTY_BYTES = ByteArray(0)
    }
}

private fun ByteArray.sum32(): Int =
    fold(0) { acc, byte -> acc + (byte.toInt() and 0xFF) }

private fun Int.toAdbCommandName(): String = when (this) {
    0x4e584e43 -> "CNXN"
    0x4e45504f -> "OPEN"
    0x59414b4f -> "OKAY"
    0x45534c43 -> "CLSE"
    0x45545257 -> "WRTE"
    0x48545541 -> "AUTH"
    else -> "0x${toUInt().toString(16)}"
}

private fun ByteArray.sliceBlock(index: Int, blockSize: Int): ByteArray {
    if (index < 0) {
        throw AdbSideloadException("Invalid sideload block index: $index")
    }
    val start = index.toLong() * blockSize.toLong()
    if (start >= size.toLong()) {
        return ByteArray(0)
    }
    val end = min(size.toLong(), start + blockSize.toLong()).toInt()
    return copyOfRange(start.toInt(), end)
}
