package io.github.lumkit.tweak.common.utils.battery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UidPowerMathTest {
    private val dump = """
        Battery History
        Estimated power use (mAh):
          Capacity: 5000, Computed drain: 12.3
          UID u0a224: 1.28 fgs: 1.17
          UID u0a276: 0.304 fg: 0.301
          UID 1000: 0.5
        Per-app wake locks:
    """.trimIndent()

    @Test
    fun parseAndroidUidToken() {
        assertEquals(10224, UidPowerMath.parseAndroidUidToken("u0a224"))
        assertEquals(1000, UidPowerMath.parseAndroidUidToken("1000"))
        assertNull(UidPowerMath.parseAndroidUidToken("screen"))
    }

    @Test
    fun parseEstimatedPower_readsUidLines() {
        val map = UidPowerMath.parseEstimatedPower(dump)
        assertEquals(1.28, map.getValue(10224).totalMah, 1e-6)
        assertEquals(1.17, map.getValue(10224).fgsMah!!, 1e-6)
        assertEquals(0.304, map.getValue(10276).totalMah, 1e-6)
        assertEquals(0.5, map.getValue(1000).totalMah, 1e-6)
        assertTrue(!map.containsKey(-1))
    }

    @Test
    fun diff_positiveDeltas() {
        val start = mapOf(10224 to UidPowerReading(10224, 1.0, null, null, null, null))
        val end = mapOf(10224 to UidPowerReading(10224, 1.5, null, null, null, null))
        val d = UidPowerMath.diff(start, end)
        assertEquals(1, d.size)
        assertEquals(0.5, d[0].deltaMah, 1e-6)
    }

    @Test
    fun diff_skipsNegativeUidKeepsPositive() {
        val start = mapOf(
            1 to UidPowerReading(1, 2.0, null, null, null, null),
            2 to UidPowerReading(2, 1.0, null, null, null, null),
        )
        val end = mapOf(
            1 to UidPowerReading(1, 1.0, null, null, null, null),
            2 to UidPowerReading(2, 1.5, null, null, null, null),
        )
        val d = UidPowerMath.diff(start, end)
        assertEquals(1, d.size)
        assertEquals(2, d[0].uid)
        assertEquals(0.5, d[0].deltaMah, 1e-6)
    }
}
