package io.github.lumkit.tweak.server.battery

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplogCodecTest {

    @Test
    fun headerMagicIsTwA1() {
        val header = ApplogWriter.encodeHeader(
            ApplogWriter.SessionMeta(
                startedAt = 1_700_000_000_000L,
                state = 0,
                intervalMs = 1000,
                createdAt = 1_700_000_000_000L,
            ),
        )
        assertTrue(ApplogWriter.isTwA1(header))
        assertEquals(64, header.size)
    }

    @Test
    fun recordRoundTrip() {
        val bytes = ApplogWriter.encodeRecord(1_700_000_000_123L, "com.example.app")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1_700_000_000_123L, buf.long)
        val len = buf.short.toInt() and 0xFFFF
        val name = ByteArray(len).also { buf.get(it) }
        assertEquals("com.example.app", name.decodeToString())
    }

    @Test
    fun parseFocusPackageFromDumpsysLine() {
        val dump = "  mCurrentFocus=Window{abc u0 com.tencent.mm/com.tencent.mm.ui.LauncherUI}"
        assertEquals("com.tencent.mm", ForegroundPackageReader.parseFocusPackage(dump))
    }
}
