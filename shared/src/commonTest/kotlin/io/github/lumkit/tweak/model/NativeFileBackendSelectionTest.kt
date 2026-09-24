package io.github.lumkit.tweak.model

import io.github.lumkit.tweak.common.utils.NativeFileBackend
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeFileBackendSelectionTest {
    @Test
    fun nullModeUsesUserBackend() {
        assertEquals(NativeFileBackend.User, selectNativeFileBackend(null))
    }

    @Test
    fun knownModesKeepExistingMapping() {
        assertEquals(NativeFileBackend.User, selectNativeFileBackend(RuntimeMode.Unknow))
        assertEquals(NativeFileBackend.ROOT, selectNativeFileBackend(RuntimeMode.Root))
        assertEquals(NativeFileBackend.SHIZUKU, selectNativeFileBackend(RuntimeMode.Shizuku))
    }
}
