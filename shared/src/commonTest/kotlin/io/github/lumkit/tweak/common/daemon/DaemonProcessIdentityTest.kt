package io.github.lumkit.tweak.common.daemon

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaemonProcessIdentityTest {

    @Test
    fun blankOrUnreadableCmdlineIsNotTweakServer() {
        assertFalse(DaemonProcessIdentity.isTweakServerCmdline(null))
        assertFalse(DaemonProcessIdentity.isTweakServerCmdline(""))
        assertFalse(DaemonProcessIdentity.isTweakServerCmdline("   "))
    }

    @Test
    fun reusedPidOfAnotherAppIsNotTweakServer() {
        assertFalse(DaemonProcessIdentity.isTweakServerCmdline("com.tencent.mobileqq:MSF"))
        assertFalse(
            DaemonProcessIdentity.isTweakServerCmdline(
                "io.github.lumkit.tweak:file_service",
            ),
        )
    }

    @Test
    fun appProcessNiceNameIsTweakServer() {
        assertTrue(
            DaemonProcessIdentity.isTweakServerCmdline(
                "/system/bin/app_process -Djava.class.path=/data/local/tmp/tweak-alpha/daemon/server.apk " +
                    "/system/bin --nice-name=tweak_server " +
                    "io.github.lumkit.tweak.server.TweakServerMain --package=io.github.lumkit.tweak",
            ),
        )
        assertTrue(DaemonProcessIdentity.isTweakServerCmdline("tweak_server"))
    }
}
