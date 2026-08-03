package io.github.lumkit.tweak.common.utils.battery

/**
 * batterystats「Estimated power use (mAh)」解析与会话差分。
 */
data class UidPowerReading(
    val uid: Int,
    val totalMah: Double,
    val fgMah: Double? = null,
    val bgMah: Double? = null,
    val fgsMah: Double? = null,
    val cachedMah: Double? = null,
)

data class UidPowerDelta(
    val uid: Int,
    val deltaMah: Double,
    val fgMah: Double? = null,
    val bgMah: Double? = null,
    val fgsMah: Double? = null,
)

object UidPowerMath {

    private val uidLineRegex =
        Regex("""^\s*UID\s+(\S+):\s*([0-9]+(?:\.[0-9]+)?)\s*(.*)$""")
    private val taggedMahRegex =
        Regex("""\b(fg|fgs|bg|cached):\s*([0-9]+(?:\.[0-9]+)?)""")
    private val appUidRegex = Regex("""^u(\d+)a(\d+)$""")

    fun parseAndroidUidToken(token: String): Int? {
        val trimmed = token.trim()
        trimmed.toIntOrNull()?.let { return it }
        val m = appUidRegex.matchEntire(trimmed) ?: return null
        val appId = m.groupValues[2].toIntOrNull() ?: return null
        return 10000 + appId
    }

    fun parseEstimatedPower(raw: String): Map<Int, UidPowerReading> {
        val lines = raw.lineSequence().toList()
        val start = lines.indexOfFirst { it.contains("Estimated power use (mAh):") }
        if (start < 0) return emptyMap()

        val out = linkedMapOf<Int, UidPowerReading>()
        for (i in (start + 1) until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (out.isNotEmpty()) break
                continue
            }
            if (trimmed.startsWith("Capacity:", ignoreCase = true) ||
                trimmed.startsWith("Computed drain:", ignoreCase = true)
            ) {
                continue
            }
            val uidMatch = uidLineRegex.matchEntire(line)
            if (uidMatch == null) {
                // 非缩进的新大段标题，或无法识别的行：已有 UID 则停止
                if (!line.startsWith(" ") && !line.startsWith("\t") && out.isNotEmpty()) {
                    break
                }
                continue
            }
            val uid = parseAndroidUidToken(uidMatch.groupValues[1]) ?: continue
            val total = uidMatch.groupValues[2].toDoubleOrNull() ?: continue
            var fg: Double? = null
            var bg: Double? = null
            var fgs: Double? = null
            var cached: Double? = null
            for (tag in taggedMahRegex.findAll(uidMatch.groupValues[3])) {
                val value = tag.groupValues[2].toDoubleOrNull() ?: continue
                when (tag.groupValues[1]) {
                    "fg" -> fg = value
                    "fgs" -> fgs = value
                    "bg" -> bg = value
                    "cached" -> cached = value
                }
            }
            out[uid] = UidPowerReading(
                uid = uid,
                totalMah = total,
                fgMah = fg,
                bgMah = bg,
                fgsMah = fgs,
                cachedMah = cached,
            )
        }
        return out
    }

    /**
     * @return 差分列表；若任一侧出现负 Δ（stats reset）则返回 null。
     */
    fun diff(
        start: Map<Int, UidPowerReading>,
        end: Map<Int, UidPowerReading>,
    ): List<UidPowerDelta>? {
        val deltas = ArrayList<UidPowerDelta>(end.size)
        for ((uid, endReading) in end) {
            val startTotal = start[uid]?.totalMah ?: 0.0
            val delta = endReading.totalMah - startTotal
            if (delta < 0.0) return null
            if (delta <= 0.0) continue
            val startReading = start[uid]
            deltas += UidPowerDelta(
                uid = uid,
                deltaMah = delta,
                fgMah = taggedDelta(startReading?.fgMah, endReading.fgMah),
                bgMah = taggedDelta(startReading?.bgMah, endReading.bgMah),
                fgsMah = taggedDelta(startReading?.fgsMah, endReading.fgsMah),
            )
        }
        return deltas
    }

    private fun taggedDelta(start: Double?, end: Double?): Double? {
        if (end == null) return null
        val d = end - (start ?: 0.0)
        return if (d > 0.0) d else null
    }
}
