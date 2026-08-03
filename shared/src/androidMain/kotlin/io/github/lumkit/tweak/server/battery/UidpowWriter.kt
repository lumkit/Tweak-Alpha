package io.github.lumkit.tweak.server.battery

import android.system.Os
import io.github.lumkit.tweak.common.utils.battery.UidpowCodec
import io.github.lumkit.tweak.common.utils.battery.UidpowFrame
import io.github.lumkit.tweak.common.utils.battery.UidpowHeader
import io.github.lumkit.tweak.common.utils.logE
import java.io.File
import java.io.RandomAccessFile

/**
 * 旁路 UID 功耗快照：`{startedAt}_{state}.uidpow`（JSON 头 + JSON Lines）。
 */
class UidpowWriter(
    private val logsDir: File,
) {
    private var startedAt: Long = -1L
    private var state: Int = -1
    private var path: File? = null

    fun openSession(startedAt: Long, state: Int) {
        this.startedAt = startedAt
        this.state = state
        logsDir.mkdirs()
        runCatching { Os.chmod(logsDir.absolutePath, 511) }
        path = partFile(logsDir, startedAt, state)
        ensureHeader()
    }

    fun appendFrame(frame: UidpowFrame) {
        val file = path ?: return
        ensureHeader()
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(raf.length())
                raf.write((UidpowCodec.encodeFrameLine(frame) + "\n").toByteArray(Charsets.UTF_8))
                raf.fd.sync()
            }
            runCatching { Os.chmod(file.absolutePath, 420) }
        } catch (e: Exception) {
            logE("uidpow append failed: ${e.message}", e, TAG)
        }
    }

    fun closeSession() {
        path = null
        startedAt = -1L
        state = -1
    }

    private fun ensureHeader() {
        val file = path ?: return
        if (file.isFile && file.length() > 0L) return
        try {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0)
                val header = UidpowHeader(startedAt = startedAt, state = state)
                raf.write((UidpowCodec.encodeHeader(header) + "\n").toByteArray(Charsets.UTF_8))
                raf.fd.sync()
            }
            runCatching { Os.chmod(file.absolutePath, 420) }
        } catch (e: Exception) {
            logE("uidpow header failed: ${e.message}", e, TAG)
        }
    }

    companion object {
        private const val TAG = "UidpowWriter"

        fun partFile(dir: File, startedAt: Long, state: Int): File =
            File(dir, "${startedAt}_${state}.uidpow")
    }
}
