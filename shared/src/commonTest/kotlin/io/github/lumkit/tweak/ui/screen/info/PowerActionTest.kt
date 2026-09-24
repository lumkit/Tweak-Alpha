package io.github.lumkit.tweak.ui.screen.info

import io.github.lumkit.tweak.model.RuntimeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerActionTest {
    @Test
    fun softRebootAndEdlRequireRoot() {
        assertFalse(PowerAction.SoftReboot.isEnabled(RuntimeMode.Shizuku))
        assertTrue(PowerAction.SoftReboot.isEnabled(RuntimeMode.Root))
        assertFalse(PowerAction.Edl.isEnabled(RuntimeMode.Shizuku))
        assertTrue(PowerAction.Reboot.isEnabled(RuntimeMode.Shizuku))
    }

    @Test
    fun softRebootScriptMatchesKernelsuFallback() {
        val script = PowerAction.SoftReboot.shellScript()
        assertTrue(script.contains("/data/adb/ksud soft-reboot"))
        assertTrue(script.contains("setprop ctl.restart zygote"))
        assertTrue(script.trimEnd().endsWith("echo __TWEAK_EXIT:$?"))
    }

    @Test
    fun exitMarkerParsesLastCode() {
        assertEquals(0, parsePowerExit("ok\n__TWEAK_EXIT:0\n"))
        assertEquals(1, parsePowerExit("__TWEAK_EXIT:1"))
        assertEquals(null, parsePowerExit("no marker"))
    }
}
