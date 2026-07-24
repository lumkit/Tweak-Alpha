package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "AccessibilityBootstrap"

private val accessibilityComponent: String
    get() = "${application.packageName}/io.github.lumkit.tweak.service.TweakAccessibilityService"

actual suspend fun ensureAccessibilityServiceEnabled(): Boolean =
    AccessibilityBootstrap.enableIfPrivileged()

/**
 * 通过特权 Shell 启用无障碍服务，供开机广播 / Splash 等调用。
 * KeepAlive / UpdateEngine 应由 [io.github.lumkit.tweak.service.TweakAccessibilityService] 拉起。
 */
object AccessibilityBootstrap {

    suspend fun enableIfPrivileged(): Boolean = withContext(Dispatchers.IO) {
        val mode = TweakDataStore.runtimeModeFlow().first()
        val privileged = when (mode) {
            RuntimeMode.Root -> runCatching { LibSuX.checkRoot() }.getOrDefault(false)
            RuntimeMode.Shizuku -> ShizukuX.checkShizuku()
            RuntimeMode.Unknow -> false
        }
        if (!privileged) {
            logD("privilege check failed, mode=$mode", TAG)
            return@withContext false
        }
        enableAccessibilityService()
        true
    }

    suspend fun enableAccessibilityService() = withContext(Dispatchers.IO) {
        val component = accessibilityComponent
        val current = ReusableShells.execSync("settings get secure enabled_accessibility_services").trim()

        val cleaned = if (current.contains(component)) {
            current.split(":").filter { it != component }.joinToString(":")
        } else {
            current
        }

        val newValue = if (cleaned.isNotEmpty() && cleaned != "null") {
            "$cleaned:$component"
        } else {
            component
        }

        ReusableShells.execSync("settings put secure enabled_accessibility_services '$newValue'")
        ReusableShells.execSync("settings put secure accessibility_enabled 1")

        val miuiCurrent = ReusableShells.execSync("settings get secure permitted_accessibility_services").trim()
        if (!miuiCurrent.contains(component)) {
            val miuiNewValue = if (miuiCurrent.isNotEmpty() && miuiCurrent != "null") {
                "$miuiCurrent:$component"
            } else {
                component
            }
            ReusableShells.execSync("settings put secure permitted_accessibility_services '$miuiNewValue'")
            logI("permitted_accessibility_services=$miuiNewValue", TAG)
        }

        logI("enabled_accessibility_services=$newValue", TAG)
        val enabled = ReusableShells.execSync("settings get secure enabled_accessibility_services")
            .contains(application.packageName)
        logI("aas check: $enabled", TAG)
    }
}
