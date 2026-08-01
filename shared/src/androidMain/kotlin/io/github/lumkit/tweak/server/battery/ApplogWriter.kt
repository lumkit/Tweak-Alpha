package io.github.lumkit.tweak.server.battery

import android.system.Os
import io.github.lumkit.tweak.common.utils.logE
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * 前台包名旁路日志：64B 头 + 变长记录（timestamp + utf8 package）。
 * 文件名：`{startedAt}_{state}.applog` / `.applog.001` …
 */
class ApplogWriter(
    private val logsDir: File,
    private var maxPartBytes: Long,
) {
    data class SessionMeta(
        val startedAt: Long,
        val state: Int,
        val intervalMs: Int,
        val createdAt: Long,
        var endedAt: Long = -1L,
    )

    private var meta: SessionMeta? = null
    private var partIndex: Int = 0
    private var raf: RandomAccessFile? = null
    private var currentPath: File? = null

    fun updateMaxPartBytes(bytes: Long) {
        maxPartBytes = bytes.coerceAtLeast(64L * 1024)
    }

    fun openSession(meta: SessionMeta) {
        closeQuiet()
        this.meta = meta
        partIndex = 0
        logsDir.mkdirs()
        runCatching { Os.chmod(logsDir.absolutePath, 511) }
        openPart()
    }

    fun append(timestampMs: Long, packageName: String) {
        ensureOpen()
        rotateIfNeeded()
        val out = raf ?: return
        out.write(encodeRecord(timestampMs, packageName))
        out.fd.sync()
    }

    fun closeSession(endedAt: Long) {
        val session = meta ?: return
        session.endedAt = endedAt
        rewriteHeader(session)
        closeQuiet()
        meta = null
    }

    private fun ensureOpen() {
        if (raf != null) return
        openPart()
    }

    private fun rotateIfNeeded() {
        val out = raf ?: return
        if (out.filePointer < maxPartBytes) return
        closeQuiet()
        partIndex += 1
        openPart()
    }

    private fun openPart() {
        val session = meta ?: return
        while (true) {
            val path = partFile(logsDir, session.startedAt, session.state, partIndex)
            val exists = path.isFile && path.length() > 0L
            if (exists && path.length() >= maxPartBytes) {
                partIndex += 1
                continue
            }
            path.parentFile?.mkdirs()
            val file = RandomAccessFile(path, "rw")
            if (!exists || path.length() == 0L) {
                file.setLength(0)
                file.write(encodeHeader(session))
                file.fd.sync()
            } else {
                file.seek(path.length())
            }
            runCatching { Os.chmod(path.absolutePath, 420) }
            raf = file
            currentPath = path
            return
        }
    }

    private fun rewriteHeader(session: SessionMeta) {
        val out = raf ?: return
        val path = currentPath ?: return
        if (path.length() < HEADER_SIZE.toLong()) return
        val pos = out.filePointer
        runCatching {
            out.seek(0)
            out.write(encodeHeader(session))
            out.seek(pos.coerceAtLeast(HEADER_SIZE.toLong()))
            out.fd.sync()
        }.onFailure {
            logE("applog rewrite header failed: ${it.message}", it, TAG)
        }
    }

    private fun closeQuiet() {
        runCatching { raf?.close() }
        raf = null
        currentPath = null
    }

    companion object {
        private const val TAG = "ApplogWriter"
        const val VERSION: Short = 1
        const val HEADER_SIZE: Short = 64
        private const val MAX_PACKAGE_BYTES = 512

        fun partFile(logsDir: File, startedAt: Long, state: Int, partIndex: Int): File {
            val base = "${startedAt}_${state}.applog"
            return if (partIndex <= 0) {
                File(logsDir, base)
            } else {
                File(logsDir, "$base.%03d".format(partIndex))
            }
        }

        fun encodeHeader(session: SessionMeta): ByteArray {
            val buf = ByteBuffer.allocate(HEADER_SIZE.toInt()).order(ByteOrder.LITTLE_ENDIAN)
            buf.put('T'.code.toByte())
            buf.put('W'.code.toByte())
            buf.put('A'.code.toByte())
            buf.put('1'.code.toByte())
            buf.putShort(VERSION)
            buf.putShort(HEADER_SIZE)
            buf.putInt(session.intervalMs)
            buf.putInt(session.state)
            buf.putLong(session.startedAt)
            buf.putLong(session.createdAt)
            buf.putLong(session.endedAt)
            while (buf.position() < HEADER_SIZE.toInt()) {
                buf.put(0)
            }
            return buf.array()
        }

        fun encodeRecord(timestampMs: Long, packageName: String): ByteArray {
            val nameBytes = packageName.toByteArray(StandardCharsets.UTF_8)
                .let { if (it.size <= MAX_PACKAGE_BYTES) it else it.copyOf(MAX_PACKAGE_BYTES) }
            val buf = ByteBuffer.allocate(8 + 2 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putLong(timestampMs)
            buf.putShort(nameBytes.size.toShort())
            buf.put(nameBytes)
            return buf.array()
        }

        fun isTwA1(header: ByteArray): Boolean =
            header.size >= 4 &&
                header[0] == 'T'.code.toByte() &&
                header[1] == 'W'.code.toByte() &&
                header[2] == 'A'.code.toByte() &&
                header[3] == '1'.code.toByte()
    }
}
