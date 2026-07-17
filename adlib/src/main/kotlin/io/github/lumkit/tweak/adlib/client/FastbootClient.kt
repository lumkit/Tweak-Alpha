package io.github.lumkit.tweak.adlib.client

import io.github.lumkit.tweak.adlib.exception.FastbootCommandException
import io.github.lumkit.tweak.adlib.exception.FastbootException
import io.github.lumkit.tweak.adlib.model.FastbootResponse
import io.github.lumkit.tweak.adlib.transport.FastbootTransport
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.FileChannel

/**
 * Fastboot 协议客户端。
 *
 * 架构位置：
 * ```
 * Tweak
 *   │
 * FastbootClient     <-- 当前类：负责 Fastboot 协议的编解码与命令封装
 *   │
 * FastbootTransport  <-- 只负责收发字节
 * ```
 *
 * 该类将高层命令（`getvar`、`flash`、`erase`、`reboot` 等）翻译为 Fastboot 协议
 * 报文，通过 [transport] 发送，并解析设备返回的 `OKAY` / `FAIL` / `DATA` / `INFO`
 * 响应。
 *
 * 非线程安全，同一实例的命令应串行调用。
 */
class FastbootClient(
    private val transport: FastbootTransport,
) : Closeable {

    private val responseBuffer = ByteArray(RESPONSE_BUFFER_SIZE)

    /**
     * 读取设备变量，例如 `product`、`serialno`、`unlocked`、`current-slot` 等。
     *
     * @return 变量值；若设备返回 FAIL 则抛出 [FastbootCommandException]。
     */
    fun getVar(name: String): String {
        val command = "getvar:$name"
        return when (val response = executeCommand(command)) {
            is FastbootResponse.Okay -> response.payload
            is FastbootResponse.Fail -> throw FastbootCommandException(command, response.reason)
            is FastbootResponse.Data -> throw FastbootException("Unexpected DATA response for $command")
        }
    }

    /**
     * 获取所有设备变量（`getvar:all`）。返回的 INFO 行形如 `name:value`。
     */
    fun getAllVars(): Map<String, String> {
        val command = "getvar:all"
        val response = executeCommand(command)
        val infos = when (response) {
            is FastbootResponse.Okay -> response.infos
            is FastbootResponse.Fail -> throw FastbootCommandException(command, response.reason)
            is FastbootResponse.Data -> throw FastbootException("Unexpected DATA response for $command")
        }
        return infos.mapNotNull { line ->
            val index = line.indexOf(':')
            if (index <= 0) return@mapNotNull null
            line.substring(0, index).trim() to line.substring(index + 1).trim()
        }.toMap()
    }

    /**
     * 将 [image] 数据下载到设备缓冲区后写入指定 [partition]。
     *
     * 内部流程：`download:<hex-size>` -> 传输数据 -> `flash:<partition>`。
     *
     * @param onProgress 数据传输进度回调（已发送字节数, 总字节数）。
     */
    fun flash(
        partition: String,
        image: ByteArray,
        onProgress: ((sent: Int, total: Int) -> Unit)? = null,
    ) {
        download(image, onProgress)
        flashDownloaded(partition)
    }

    fun flash(
        partition: String,
        input: InputStream,
        size: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ) {
        require(size in 0..UInt.MAX_VALUE.toLong()) {
            "Image size $size exceeds fastboot protocol limit"
        }
        download(input, size, onProgress)
        flashDownloaded(partition)
    }

    fun flash(
        partition: String,
        channel: FileChannel,
        size: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ) {
        channel.position(0)
        if (isSparseImage(channel, size)) {
            flashSparse(partition, channel, size, onProgress)
            return
        }
        require(size in 0..UInt.MAX_VALUE.toLong()) {
            "Image size $size exceeds fastboot protocol limit"
        }
        channel.position(0)
        download(Channels.newInputStream(channel), size, onProgress)
        flashDownloaded(partition)
    }

    /**
     * 擦除指定分区。
     */
    fun erase(partition: String) {
        val command = "erase:$partition"
        when (val response = executeCommand(command, timeoutMillis = FLASH_TIMEOUT_MILLIS)) {
            is FastbootResponse.Okay -> Unit
            is FastbootResponse.Fail -> throw FastbootCommandException(command, response.reason)
            is FastbootResponse.Data -> throw FastbootException("Unexpected DATA response for $command")
        }
    }

    /**
     * 设置当前活动 slot（A/B 设备），[slot] 取值 `a` 或 `b`。
     */
    fun setActiveSlot(slot: String) {
        runSimpleCommand("set_active:$slot")
    }

    /** 重启到系统。 */
    fun reboot() = runSimpleCommand("reboot")

    /** 重启到 bootloader。 */
    fun rebootBootloader() = runSimpleCommand("reboot-bootloader")

    /** 执行一个不需要数据阶段、仅关心成功与否的命令。 */
    fun runSimpleCommand(command: String): FastbootResponse.Okay {
        return when (val response = executeCommand(command)) {
            is FastbootResponse.Okay -> response
            is FastbootResponse.Fail -> throw FastbootCommandException(command, response.reason)
            is FastbootResponse.Data -> throw FastbootException("Unexpected DATA response for $command")
        }
    }

    /**
     * 下载数据阶段：发送 `download:<hex-size>`，等待 DATA 响应后传输 [data]。
     */
    private fun download(data: ByteArray, onProgress: ((sent: Int, total: Int) -> Unit)?) {
        val total = data.size.toLong()
        val command = "download:${hexSize(total)}"
        val response = executeCommand(command)
        val expected = when (response) {
            is FastbootResponse.Data -> response.size
            is FastbootResponse.Fail -> throw FastbootCommandException(command, response.reason)
            is FastbootResponse.Okay -> throw FastbootException("Device did not enter data phase for $command")
        }
        if (expected != total) {
            throw FastbootException("Device expects $expected bytes but image is ${data.size} bytes")
        }

        var offset = 0
        while (offset < data.size) {
            val chunk = minOf(MAX_CHUNK_SIZE, data.size - offset)
            val sent = transport.send(data, offset, chunk, timeoutMillis = FLASH_TIMEOUT_MILLIS)
            if (sent <= 0) {
                throw FastbootException("USB transfer stalled while downloading image")
            }
            offset += sent
            onProgress?.invoke(offset, data.size)
        }

        // 数据阶段结束后设备回复最终状态。
        when (val finish = readResponse()) {
            is FastbootResponse.Okay -> Unit
            is FastbootResponse.Fail -> throw FastbootCommandException(command, finish.reason)
            is FastbootResponse.Data -> throw FastbootException("Unexpected DATA response after download")
        }
    }

    private fun download(
        input: InputStream,
        size: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ) {
        val total = size
        val command = "download:${hexSize(total)}"
        val response = executeCommand(command)
        val expected = when (response) {
            is FastbootResponse.Data -> response.size
            is FastbootResponse.Fail -> throw FastbootCommandException(command, response.reason)
            is FastbootResponse.Okay -> throw FastbootException("Device did not enter data phase for $command")
        }
        if (expected != total) {
            throw FastbootException("Device expects $expected bytes but image is $total bytes")
        }

        val buffer = ByteArray(MAX_CHUNK_SIZE)
        var sentBytes = 0L
        while (sentBytes < size) {
            val maxRead = minOf(buffer.size.toLong(), size - sentBytes).toInt()
            val read = input.read(buffer, 0, maxRead)
            if (read < 0) {
                throw FastbootException("Unexpected EOF while reading image stream, sent=$sentBytes, total=$size")
            }
            var offset = 0
            while (offset < read) {
                val sent = transport.send(buffer, offset, read - offset, timeoutMillis = FLASH_TIMEOUT_MILLIS)
                if (sent <= 0) {
                    throw FastbootException("USB transfer stalled while downloading image")
                }
                offset += sent
                sentBytes += sent
                onProgress?.invoke(sentBytes, size)
            }
        }

        if (input.read() >= 0) {
            throw FastbootException("Image stream contains more data than declared size $size")
        }

        when (val finish = readResponse()) {
            is FastbootResponse.Okay -> Unit
            is FastbootResponse.Fail -> throw FastbootCommandException(command, finish.reason)
            is FastbootResponse.Data -> throw FastbootException("Unexpected DATA response after download")
        }
    }

    private fun flashDownloaded(partition: String) {
        val command = "flash:$partition"
        when (val response = executeCommand(command, timeoutMillis = FLASH_TIMEOUT_MILLIS)) {
            is FastbootResponse.Okay -> Unit
            is FastbootResponse.Fail -> throw FastbootCommandException(command, response.reason)
            is FastbootResponse.Data -> throw FastbootException("Unexpected DATA response for $command")
        }
    }

    private fun flashSparse(
        partition: String,
        channel: FileChannel,
        size: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ) {
        val header = readSparseHeader(channel)
        val maxDownloadSize = minOf(resolveMaxDownloadSize(), UInt.MAX_VALUE.toLong())
        if (maxDownloadSize < SPARSE_IMAGE_HEADER_SIZE + SPARSE_CHUNK_HEADER_SIZE + header.blockSize) {
            throw FastbootException("Fastboot max-download-size $maxDownloadSize is too small for sparse flashing")
        }
        var currentBlock = 0L
        var builder = SparseSegmentBuilder(header, maxDownloadSize)
        var sourceProgress = header.fileHeaderSize.toLong()

        repeat(header.totalChunks) {
            val chunk = readSparseChunkHeader(channel, header.chunkHeaderSize)
            val dataOffset = channel.position()
            val chunkDataSize = chunk.totalSize - header.chunkHeaderSize
            when (chunk.type) {
                SPARSE_CHUNK_TYPE_RAW -> {
                    val expectedDataSize = chunk.blocks * header.blockSize
                    if (chunkDataSize != expectedDataSize) {
                        throw FastbootException("Invalid sparse RAW chunk size: $chunkDataSize, expected=$expectedDataSize")
                    }
                    var remainingBlocks = chunk.blocks
                    var rangeOffset = 0L
                    while (remainingBlocks > 0) {
                        val blocks = builder.maxAppendableRawBlocks(currentBlock, remainingBlocks)
                        if (blocks <= 0) {
                            flushSparseSegment(partition, channel, builder, sourceProgress, size, onProgress)
                            builder = SparseSegmentBuilder(header, maxDownloadSize)
                            continue
                        }
                        val bytes = blocks * header.blockSize
                        builder.appendRaw(currentBlock, blocks, dataOffset + rangeOffset, bytes)
                        currentBlock += blocks
                        remainingBlocks -= blocks
                        rangeOffset += bytes
                    }
                }

                SPARSE_CHUNK_TYPE_FILL -> {
                    if (chunkDataSize != SPARSE_FILL_DATA_SIZE) {
                        throw FastbootException("Invalid sparse FILL chunk size: $chunkDataSize")
                    }
                    val fill = readBytesAt(channel, dataOffset, SPARSE_FILL_DATA_SIZE)
                    var remainingBlocks = chunk.blocks
                    while (remainingBlocks > 0) {
                        val blocks = builder.maxAppendableFillBlocks(currentBlock, remainingBlocks)
                        if (blocks <= 0) {
                            flushSparseSegment(partition, channel, builder, sourceProgress, size, onProgress)
                            builder = SparseSegmentBuilder(header, maxDownloadSize)
                            continue
                        }
                        builder.appendFill(currentBlock, blocks, fill)
                        currentBlock += blocks
                        remainingBlocks -= blocks
                    }
                }

                SPARSE_CHUNK_TYPE_DONT_CARE -> currentBlock += chunk.blocks
                SPARSE_CHUNK_TYPE_CRC32 -> Unit
                else -> throw FastbootException("Unknown sparse chunk type: 0x${chunk.type.toString(16)}")
            }
            channel.position(dataOffset + chunkDataSize)
            sourceProgress = channel.position()
        }

        if (currentBlock != header.totalBlocks) {
            throw FastbootException("Sparse image block count mismatch: $currentBlock, expected=${header.totalBlocks}")
        }
        flushSparseSegment(partition, channel, builder, size, size, onProgress)
    }

    private fun flushSparseSegment(
        partition: String,
        channel: FileChannel,
        builder: SparseSegmentBuilder,
        sourceProgress: Long,
        total: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ) {
        val segment = builder.buildOrNull() ?: return
        SparseSegmentInputStream(channel, segment.pieces).use { input ->
            download(input, segment.size) { _, _ -> }
        }
        flashDownloaded(partition)
        onProgress?.invoke(sourceProgress.coerceAtMost(total), total)
    }

    private fun isSparseImage(channel: FileChannel, size: Long): Boolean {
        if (size < SPARSE_IMAGE_HEADER_SIZE) return false
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, buffer, 0)
        buffer.flip()
        return buffer.int == SPARSE_MAGIC
    }

    private fun readSparseHeader(channel: FileChannel): SparseHeader {
        val buffer = ByteBuffer.allocate(SPARSE_IMAGE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, buffer, 0)
        buffer.flip()
        val magic = buffer.int
        val majorVersion = buffer.short.toInt() and 0xFFFF
        val minorVersion = buffer.short.toInt() and 0xFFFF
        val fileHeaderSize = buffer.short.toInt() and 0xFFFF
        val chunkHeaderSize = buffer.short.toInt() and 0xFFFF
        val blockSize = Integer.toUnsignedLong(buffer.int)
        val totalBlocks = Integer.toUnsignedLong(buffer.int)
        val totalChunks = buffer.int
        buffer.int
        if (magic != SPARSE_MAGIC || majorVersion != 1 || minorVersion != 0) {
            throw FastbootException("Unsupported sparse image header")
        }
        if (fileHeaderSize != SPARSE_IMAGE_HEADER_SIZE || chunkHeaderSize != SPARSE_CHUNK_HEADER_SIZE) {
            throw FastbootException("Unsupported sparse image header size")
        }
        if (totalChunks < 0) {
            throw FastbootException("Invalid sparse chunk count: $totalChunks")
        }
        channel.position(fileHeaderSize.toLong())
        return SparseHeader(fileHeaderSize, chunkHeaderSize, blockSize, totalBlocks, totalChunks)
    }

    private fun readSparseChunkHeader(channel: FileChannel, chunkHeaderSize: Int): SparseChunkHeader {
        val position = channel.position()
        val buffer = ByteBuffer.allocate(chunkHeaderSize).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, buffer, position)
        channel.position(position + chunkHeaderSize)
        buffer.flip()
        val type = buffer.short.toInt() and 0xFFFF
        buffer.short
        val blocks = Integer.toUnsignedLong(buffer.int)
        val totalSize = Integer.toUnsignedLong(buffer.int)
        return SparseChunkHeader(type, blocks, totalSize)
    }

    private fun readBytesAt(channel: FileChannel, position: Long, size: Long): ByteArray {
        require(size <= Int.MAX_VALUE) { "Read size $size is too large" }
        val bytes = ByteArray(size.toInt())
        val buffer = ByteBuffer.wrap(bytes)
        readFully(channel, buffer, position)
        return bytes
    }

    private fun readFully(channel: FileChannel, buffer: ByteBuffer, position: Long) {
        var offset = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, offset)
            if (read < 0) throw FastbootException("Unexpected EOF while reading sparse image")
            offset += read
        }
    }

    private fun resolveMaxDownloadSize(): Long {
        val reported = runCatching { parseDownloadSize(getVar("max-download-size")) }
            .getOrNull()
            ?.takeIf { it > SPARSE_IMAGE_HEADER_SIZE + SPARSE_CHUNK_HEADER_SIZE }
        // 设备回报异常偏大时仍限制到协议可用上限，避免一次 download 整包
        val resolved = reported ?: DEFAULT_SPARSE_DOWNLOAD_SIZE
        return minOf(resolved, UInt.MAX_VALUE.toLong())
    }

    /**
     * Fastboot `max-download-size` 可能是 `0x20000000`（十六进制）或 `536870912`（十进制）。
     * 绝不能把纯十进制字符串按 hex 解析，否则会把分片上限放大几个数量级。
     */
    private fun parseDownloadSize(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("0x", ignoreCase = true)) {
            return trimmed.substring(2).toLongOrNull(16)
        }
        val hasHexLetter = trimmed.any { it in 'a'..'f' || it in 'A'..'F' }
        return if (hasHexLetter) {
            trimmed.toLongOrNull(16)
        } else {
            trimmed.toLongOrNull()
        }
    }

    private fun hexSize(size: Long): String {
        require(size in 0..UInt.MAX_VALUE.toLong()) {
            "Image size $size exceeds fastboot protocol limit"
        }
        return size.toString(16).padStart(8, '0')
    }

    private class SparseSegmentBuilder(
        private val header: SparseHeader,
        private val maxSize: Long,
    ) {
        private val pieces = mutableListOf<SparsePiece>()
        private var size = SPARSE_IMAGE_HEADER_SIZE.toLong()
        private var chunkCount = 0
        private var cursorBlock = 0L
        private var hasPayload = false

        fun maxAppendableRawBlocks(startBlock: Long, requestedBlocks: Long): Long {
            return maxAppendableBlocks(startBlock, requestedBlocks) { blocks ->
                SPARSE_CHUNK_HEADER_SIZE + blocks * header.blockSize
            }
        }

        fun maxAppendableFillBlocks(startBlock: Long, requestedBlocks: Long): Long {
            return maxAppendableBlocks(startBlock, requestedBlocks) {
                SPARSE_CHUNK_HEADER_SIZE + SPARSE_FILL_DATA_SIZE
            }
        }

        fun appendRaw(startBlock: Long, blocks: Long, dataOffset: Long, dataSize: Long) {
            appendGap(startBlock)
            pieces += SparsePiece.Bytes(chunkHeader(SPARSE_CHUNK_TYPE_RAW, blocks, SPARSE_CHUNK_HEADER_SIZE + dataSize))
            pieces += SparsePiece.FileRange(dataOffset, dataSize)
            size += SPARSE_CHUNK_HEADER_SIZE + dataSize
            chunkCount++
            cursorBlock = startBlock + blocks
            hasPayload = true
        }

        fun appendFill(startBlock: Long, blocks: Long, fill: ByteArray) {
            appendGap(startBlock)
            pieces += SparsePiece.Bytes(chunkHeader(SPARSE_CHUNK_TYPE_FILL, blocks, SPARSE_CHUNK_HEADER_SIZE + SPARSE_FILL_DATA_SIZE))
            pieces += SparsePiece.Bytes(fill)
            size += SPARSE_CHUNK_HEADER_SIZE + SPARSE_FILL_DATA_SIZE
            chunkCount++
            cursorBlock = startBlock + blocks
            hasPayload = true
        }

        fun buildOrNull(): SparseSegment? {
            if (!hasPayload) return null
            val segmentPieces = mutableListOf<SparsePiece>()
            val totalChunks = chunkCount + if (cursorBlock < header.totalBlocks) 1 else 0
            segmentPieces += SparsePiece.Bytes(sparseHeader(totalChunks))
            segmentPieces += pieces
            var segmentSize = size
            if (cursorBlock < header.totalBlocks) {
                segmentPieces += SparsePiece.Bytes(
                    chunkHeader(SPARSE_CHUNK_TYPE_DONT_CARE, header.totalBlocks - cursorBlock, SPARSE_CHUNK_HEADER_SIZE.toLong())
                )
                segmentSize += SPARSE_CHUNK_HEADER_SIZE
            }
            return SparseSegment(segmentPieces, segmentSize)
        }

        private fun maxAppendableBlocks(
            startBlock: Long,
            requestedBlocks: Long,
            chunkSize: (Long) -> Long,
        ): Long {
            var low = 0L
            var high = requestedBlocks
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (canAppend(startBlock, mid, chunkSize(mid))) {
                    low = mid
                } else {
                    high = mid - 1
                }
            }
            return low
        }

        private fun canAppend(startBlock: Long, blocks: Long, chunkSize: Long): Boolean {
            val gapSize = if (startBlock > cursorBlock) SPARSE_CHUNK_HEADER_SIZE.toLong() else 0L
            val endBlock = startBlock + blocks
            val trailingSize = if (endBlock < header.totalBlocks) SPARSE_CHUNK_HEADER_SIZE.toLong() else 0L
            return size + gapSize + chunkSize + trailingSize <= maxSize
        }

        private fun appendGap(startBlock: Long) {
            if (startBlock <= cursorBlock) return
            pieces += SparsePiece.Bytes(chunkHeader(SPARSE_CHUNK_TYPE_DONT_CARE, startBlock - cursorBlock, SPARSE_CHUNK_HEADER_SIZE.toLong()))
            size += SPARSE_CHUNK_HEADER_SIZE
            chunkCount++
            cursorBlock = startBlock
        }

        private fun sparseHeader(totalChunks: Int): ByteArray {
            return ByteBuffer.allocate(SPARSE_IMAGE_HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(SPARSE_MAGIC)
                .putShort(1.toShort())
                .putShort(0.toShort())
                .putShort(SPARSE_IMAGE_HEADER_SIZE.toShort())
                .putShort(SPARSE_CHUNK_HEADER_SIZE.toShort())
                .putInt(header.blockSize.toInt())
                .putInt(header.totalBlocks.toInt())
                .putInt(totalChunks)
                .putInt(0)
                .array()
        }

        private fun chunkHeader(type: Int, blocks: Long, totalSize: Long): ByteArray {
            return ByteBuffer.allocate(SPARSE_CHUNK_HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(type.toShort())
                .putShort(0)
                .putInt(blocks.toInt())
                .putInt(totalSize.toInt())
                .array()
        }
    }

    private class SparseSegmentInputStream(
        private val channel: FileChannel,
        private val pieces: List<SparsePiece>,
    ) : InputStream() {
        private var pieceIndex = 0
        private var pieceOffset = 0L

        override fun read(): Int {
            val buffer = ByteArray(1)
            val read = read(buffer, 0, 1)
            return if (read < 0) -1 else buffer[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            var offset = off
            var remaining = len
            var totalRead = 0
            while (remaining > 0 && pieceIndex < pieces.size) {
                val piece = pieces[pieceIndex]
                val read = when (piece) {
                    is SparsePiece.Bytes -> readBytesPiece(piece, buffer, offset, remaining)
                    is SparsePiece.FileRange -> readFilePiece(piece, buffer, offset, remaining)
                }
                if (read <= 0) break
                offset += read
                remaining -= read
                totalRead += read
                pieceOffset += read
                if (pieceOffset >= piece.size) {
                    pieceIndex++
                    pieceOffset = 0
                }
            }
            return if (totalRead > 0) totalRead else -1
        }

        private fun readBytesPiece(piece: SparsePiece.Bytes, buffer: ByteArray, offset: Int, length: Int): Int {
            val count = minOf(length, (piece.bytes.size - pieceOffset).toInt())
            piece.bytes.copyInto(buffer, offset, pieceOffset.toInt(), pieceOffset.toInt() + count)
            return count
        }

        private fun readFilePiece(piece: SparsePiece.FileRange, buffer: ByteArray, offset: Int, length: Int): Int {
            val count = minOf(length.toLong(), piece.length - pieceOffset).toInt()
            val byteBuffer = ByteBuffer.wrap(buffer, offset, count)
            var readTotal = 0
            while (byteBuffer.hasRemaining()) {
                val read = channel.read(byteBuffer, piece.offset + pieceOffset + readTotal)
                if (read < 0) break
                readTotal += read
            }
            return readTotal
        }
    }

    private data class SparseHeader(
        val fileHeaderSize: Int,
        val chunkHeaderSize: Int,
        val blockSize: Long,
        val totalBlocks: Long,
        val totalChunks: Int,
    )

    private data class SparseChunkHeader(
        val type: Int,
        val blocks: Long,
        val totalSize: Long,
    )

    private data class SparseSegment(
        val pieces: List<SparsePiece>,
        val size: Long,
    )

    private sealed interface SparsePiece {
        val size: Long

        data class Bytes(val bytes: ByteArray) : SparsePiece {
            override val size: Long = bytes.size.toLong()
        }

        data class FileRange(val offset: Long, val length: Long) : SparsePiece {
            override val size: Long = length
        }
    }

    /**
     * 发送一条命令并读取完整响应（自动跳过 INFO，返回终态）。
     */
    private fun executeCommand(
        command: String,
        timeoutMillis: Int = FastbootTransport.DEFAULT_TIMEOUT_MILLIS,
    ): FastbootResponse {
        val payload = command.toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_COMMAND_SIZE) {
            "Command \"$command\" exceeds $MAX_COMMAND_SIZE bytes"
        }
        transport.send(payload, timeoutMillis = timeoutMillis)
        return readResponse(timeoutMillis)
    }

    /**
     * 读取并聚合设备响应，直到遇到终态（OKAY / FAIL / DATA），期间累积 INFO。
     */
    private fun readResponse(
        timeoutMillis: Int = FastbootTransport.DEFAULT_TIMEOUT_MILLIS,
    ): FastbootResponse {
        val infos = mutableListOf<String>()
        while (true) {
            val read = transport.receive(responseBuffer, timeoutMillis)
            if (read < PREFIX_LENGTH) {
                throw FastbootException("Malformed fastboot response, received $read bytes")
            }
            val prefix = String(responseBuffer, 0, PREFIX_LENGTH, Charsets.US_ASCII)
            val body = String(responseBuffer, PREFIX_LENGTH, read - PREFIX_LENGTH, Charsets.UTF_8)
            when (prefix) {
                PREFIX_OKAY -> return FastbootResponse.Okay(body, infos.toList())
                PREFIX_FAIL -> return FastbootResponse.Fail(body, infos.toList())
                PREFIX_DATA -> {
                    val size = body.trim().toLongOrNull(radix = 16)
                        ?: throw FastbootException("Invalid DATA size: \"$body\"")
                    return FastbootResponse.Data(size, infos.toList())
                }
                PREFIX_INFO -> infos.add(body)
                else -> throw FastbootException("Unknown fastboot response prefix: \"$prefix\"")
            }
        }
    }

    override fun close() {
        transport.close()
    }

    companion object {
        private const val PREFIX_LENGTH = 4
        private const val PREFIX_OKAY = "OKAY"
        private const val PREFIX_FAIL = "FAIL"
        private const val PREFIX_DATA = "DATA"
        private const val PREFIX_INFO = "INFO"

        private const val MAX_COMMAND_SIZE = 64
        private const val RESPONSE_BUFFER_SIZE = 256
        // 读盘缓冲可大于 USB 单次上限；实际 bulk 分包由 UsbFastbootTransport 处理
        private const val MAX_CHUNK_SIZE = 256 * 1024
        private const val FLASH_TIMEOUT_MILLIS = 60_000
        private const val SPARSE_MAGIC = -316211398
        private const val SPARSE_IMAGE_HEADER_SIZE = 28
        private const val SPARSE_CHUNK_HEADER_SIZE = 12
        private const val SPARSE_FILL_DATA_SIZE = 4L
        private const val SPARSE_CHUNK_TYPE_RAW = 0xCAC1
        private const val SPARSE_CHUNK_TYPE_FILL = 0xCAC2
        private const val SPARSE_CHUNK_TYPE_DONT_CARE = 0xCAC3
        private const val SPARSE_CHUNK_TYPE_CRC32 = 0xCAC4
        // 与小米 flash 工具常见上限一致；仅在 getvar 失败时作兜底
        private const val DEFAULT_SPARSE_DOWNLOAD_SIZE = 256L * 1024L * 1024L
    }
}
