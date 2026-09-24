package io.github.lumkit.tweak.common.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrashWriteResultTest {
    @Test
    fun storedExposesPath() {
        val result: CrashWriteResult = CrashWriteResult.Stored("/data/data/app/files/crashes/client_1_2.log")
        assertEquals("/data/data/app/files/crashes/client_1_2.log", result.pathOrNull())
    }

    @Test
    fun failedHasNoPath() {
        val result: CrashWriteResult = CrashWriteResult.Failed("mkdir failed")
        assertEquals(null, result.pathOrNull())
        assertIs<CrashWriteResult.Failed>(result)
    }
}
