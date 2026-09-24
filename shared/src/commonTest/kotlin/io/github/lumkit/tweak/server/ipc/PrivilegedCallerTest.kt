package io.github.lumkit.tweak.server.ipc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivilegedCallerTest {
    @Test
    fun rootAndShellMayDeliverBinder() {
        assertTrue(isPrivilegedCaller(0))
        assertTrue(isPrivilegedCaller(2000))
    }

    @Test
    fun appUidIsRejected() {
        assertFalse(isPrivilegedCaller(10000))
        assertFalse(isPrivilegedCaller(10420))
    }
}
