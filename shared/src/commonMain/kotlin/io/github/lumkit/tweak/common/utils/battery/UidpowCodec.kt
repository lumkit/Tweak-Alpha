package io.github.lumkit.tweak.common.utils.battery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `.uidpow`：首行 header JSON + 其后每帧一行 JSON（version=1）。
 */
@Serializable
data class UidpowHeader(
    val magic: String = "TWUP",
    val version: Int = 1,
    val startedAt: Long,
    val state: Int,
)

@Serializable
data class UidpowEntry(
    val uid: Int,
    val packageName: String? = null,
    val totalMah: Double,
    val fgMah: Double? = null,
    val bgMah: Double? = null,
    val fgsMah: Double? = null,
    val cachedMah: Double? = null,
)

@Serializable
data class UidpowFrame(
    val capturedAt: Long,
    val entries: List<UidpowEntry>,
)

object UidpowCodec {
    const val MAGIC = "TWUP"
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeHeader(header: UidpowHeader): String =
        json.encodeToString(UidpowHeader.serializer(), header.copy(magic = MAGIC, version = VERSION))

    fun decodeHeader(line: String): UidpowHeader? {
        val header = runCatching {
            json.decodeFromString(UidpowHeader.serializer(), line.trim())
        }.getOrNull() ?: return null
        if (header.magic != MAGIC || header.version != VERSION) return null
        return header
    }

    fun encodeFrameLine(frame: UidpowFrame): String =
        json.encodeToString(UidpowFrame.serializer(), frame)

    fun decodeFrameLine(line: String): UidpowFrame? =
        runCatching {
            json.decodeFromString(UidpowFrame.serializer(), line.trim())
        }.getOrNull()

    fun parseFile(text: String): Pair<UidpowHeader, List<UidpowFrame>>? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return null
        val header = decodeHeader(lines.first()) ?: return null
        val frames = lines.drop(1).mapNotNull { decodeFrameLine(it) }
        return header to frames
    }
}
