package io.github.lumkit.tweak.ui.screen.info

import io.github.lumkit.tweak.model.RuntimeMode

enum class PowerAction {
    Shutdown,
    Reboot,
    SoftReboot,
    Recovery,
    Bootloader,
    Edl,
}

fun PowerAction.isEnabled(mode: RuntimeMode): Boolean = when (this) {
    PowerAction.SoftReboot, PowerAction.Edl -> mode == RuntimeMode.Root
    else -> mode == RuntimeMode.Root || mode == RuntimeMode.Shizuku
}

fun PowerAction.shellScript(): String {
    val body = when (this) {
        PowerAction.Shutdown ->
            "/system/bin/svc power shutdown || /system/bin/reboot -p || /system/bin/setprop sys.powerctl shutdown"
        PowerAction.Reboot ->
            "/system/bin/svc power reboot || /system/bin/reboot || /system/bin/setprop sys.powerctl reboot"
        PowerAction.SoftReboot ->
            "if [ -x /data/adb/ksud ]; then /data/adb/ksud soft-reboot; else setprop ctl.restart zygote; fi"
        PowerAction.Recovery ->
            "/system/bin/svc power reboot recovery || /system/bin/reboot recovery || /system/bin/setprop sys.powerctl reboot,recovery"
        PowerAction.Bootloader ->
            "/system/bin/svc power reboot bootloader || /system/bin/reboot bootloader || /system/bin/setprop sys.powerctl reboot,bootloader"
        PowerAction.Edl ->
            "/system/bin/reboot edl || /system/bin/setprop sys.powerctl reboot,edl"
    }
    return "$body\necho __TWEAK_EXIT:$?"
}

fun parsePowerExit(output: String): Int? {
    return output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("__TWEAK_EXIT:") }
        .lastOrNull()
        ?.removePrefix("__TWEAK_EXIT:")
        ?.toIntOrNull()
}
