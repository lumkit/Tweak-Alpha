package io.github.lumkit.tweak.server.battery

import android.system.Os
import io.github.lumkit.tweak.common.database.battery.BatteryRecordDefaults
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * v2 定长电池日志：64B 头 + 32B 样本。
 * 文件名：`{startedAt}_{state}.brlog` / `.brlog.001` …
 */
class BrlogWriter(
    private val logsDir: File,
    private var maxPartBytes: Long,
) {
    data class SessionMeta(
        val startedAt: Long,
        val intervalMs: Int,
        val state: Int,
        val confirmMs: Int,
        val createdAt: Long,
        var confirmed: Boolean,
        var deleted: Boolean,
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

    fun appendSample(sample: BatterySample) {
        if (!sample.hasValidLevel) return
        ensureOpen()
        rotateIfNeeded()
        val out = raf ?: return
        val buf = ByteBuffer.allocate(RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putLong(sample.timestampMs)
        buf.putInt(sample.level)
        buf.putInt(sample.voltageMv)
        buf.putInt(sample.currentMa)
        buf.putShort(sample.tempCenti)
        buf.put(if (sample.screenOn) 1 else 0)
        buf.put(0)
        buf.put(0)
        buf.put(0)
        out.write(buf.array())
        out.fd.sync()
    }

    fun markConfirmed() {
        val session = meta ?: return
        session.confirmed = true
        rewriteHeader(session)
    }

    fun markDeleted() {
        val session = meta ?: return
        session.deleted = true
        rewriteHeader(session)
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
            logE("rewrite header failed: ${it.message}", it, TAG)
        }
    }

    private fun closeQuiet() {
        runCatching { raf?.close() }
        raf = null
        currentPath = null
    }

    companion object {
        private const val TAG = "BrlogWriter"
        const val VERSION: Short = 2
        const val HEADER_SIZE: Short = 64
        const val RECORD_SIZE = 32

        fun partFile(logsDir: File, startedAt: Long, state: Int, partIndex: Int): File {
            val base = "${startedAt}_${state}.brlog"
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
            buf.put('B'.code.toByte())
            buf.put('2'.code.toByte())
            buf.putShort(VERSION)
            buf.putShort(HEADER_SIZE)
            buf.putShort(RECORD_SIZE.toShort())
            buf.putShort(0)
            buf.putInt(session.intervalMs)
            buf.putInt(session.state)
            buf.putInt(session.confirmMs)
            buf.putLong(session.startedAt)
            buf.putLong(session.createdAt)
            buf.put(if (session.confirmed) 1 else 0)
            buf.put(if (session.deleted) 1 else 0)
            buf.putShort(0)
            buf.putLong(session.endedAt)
            while (buf.position() < HEADER_SIZE.toInt()) {
                buf.put(0)
            }
            return buf.array()
        }
    }
}

/** 供引擎引用默认 confirm，避免到处硬编码 */
internal val DefaultConfirmMs: Int = BatteryRecordDefaults.CONFIRM_MS
