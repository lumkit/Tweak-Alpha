package io.github.lumkit.tweak.common.utils.battery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UidpowCodecTest {

    @Test
    fun roundTripHeaderAndFrames() {
        val header = UidpowHeader(startedAt = 100L, state = 0)
        val frame1 = UidpowFrame(
            capturedAt = 100L,
            entries = listOf(UidpowEntry(uid = 10224, totalMah = 1.0, fgsMah = 0.5)),
        )
        val frame2 = UidpowFrame(
            capturedAt = 200L,
            entries = listOf(UidpowEntry(uid = 10224, totalMah = 1.5)),
        )
        val text = buildString {
            appendLine(UidpowCodec.encodeHeader(header))
            appendLine(UidpowCodec.encodeFrameLine(frame1))
            append(UidpowCodec.encodeFrameLine(frame2))
        }
        val parsed = UidpowCodec.parseFile(text)
        assertNotNull(parsed)
        assertEquals(100L, parsed.first.startedAt)
        assertEquals(0, parsed.first.state)
        assertEquals(2, parsed.second.size)
        assertEquals(1.5, parsed.second[1].entries.single().totalMah, 1e-6)
    }

    @Test
    fun badMagicReturnsNull() {
        val bad = """{"magic":"XXXX","version":1,"startedAt":1,"state":0}"""
        assertNull(UidpowCodec.decodeHeader(bad))
        assertNull(UidpowCodec.parseFile(bad))
    }

    @Test
    fun encodeHeaderUsesTwupMagic() {
        val line = UidpowCodec.encodeHeader(UidpowHeader(magic = "NOPE", version = 99, startedAt = 1, state = 0))
        assertTrue(line.contains("\"TWUP\""))
        assertTrue(line.contains("\"version\":1"))
    }
}
