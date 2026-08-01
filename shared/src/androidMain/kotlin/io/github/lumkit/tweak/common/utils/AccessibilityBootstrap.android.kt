package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.service.TweakAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "AccessibilityBootstrap"

actual object AccessibilityBootstrap {

    actual val serviceComponent: String
        get() = "${application.packageName}/${ConstCommon.Accessibility.SERVICE_CLASS}"

    actual suspend fun isAccessibilityServiceEnabled(): Boolean = withContext(Dispatchers.IO) {
        val current = readEnabledServices()
        current.any { it == serviceComponent || it.contains(application.packageName) }
    }

    actual suspend fun isAccessibilityServiceConnected(): Boolean =
        TweakAccessibilityService.overlayContextOrNull != null

    actual suspend fun startAccessibilityService(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val component = serviceComponent
            val services = readEnabledServices().toMutableList()
            if (component !in services) {
                services += component
            }
            val enabledServices = services.joinToString(":")
            ReusableShells.execSync(
                """
                settings put secure enabled_accessibility_services ${shellQuote(enabledServices)}
                settings put secure accessibility_enabled 1
                """.trimIndent(),
            )
            ensureMiuiPermitted(component)
            logI("startAccessibilityService => $enabledServices", TAG)
            true
        }.onFailure {
            logE("startAccessibilityService failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun stopAccessibilityService(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val component = serviceComponent
            val current = readEnabledServicesRaw()
            if (!current.contains(component) && !current.contains(application.packageName)) {
                logD("stopAccessibilityService: already off", TAG)
                return@runCatching true
            }
            val remaining = readEnabledServices()
                .filterNot { it == component || it.contains(application.packageName) }
            val newValue = remaining.joinToString(":")
            ReusableShells.execSync(
                """
                settings put secure enabled_accessibility_services ${shellQuote(newValue)}
                settings put secure accessibility_enabled 1
                """.trimIndent(),
            )
            logI("stopAccessibilityService => '$newValue'", TAG)
            true
        }.onFailure {
            logE("stopAccessibilityService failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun enableIfPrivileged(requireUserPreference: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (requireUserPreference && !TweakDataStore.a11yDaemonEnabledFlow().first()) {
                logD("user preference off, skip enable", TAG)
                return@withContext false
            }
            if (!hasPrivilege()) {
                logD("privilege check failed", TAG)
                return@withContext false
            }
            startAccessibilityService()
        }

    actual suspend fun ensureRunningIfUserEnabled(): Boolean = withContext(Dispatchers.IO) {
        if (!TweakDataStore.a11yDaemonEnabledFlow().first()) {
            logD("user preference off, skip ensure", TAG)
            return@withContext false
        }
        if (!hasPrivilege()) {
            logD("privilege check failed, skip ensure", TAG)
            return@withContext false
        }
        if (isAccessibilityServiceConnected() || isAccessibilityServiceEnabled()) {
            // settings 已开但未 connected：再 put 一次促发系统重绑
            if (!isAccessibilityServiceConnected()) {
                logD("in settings but not connected, re-start", TAG)
                return@withContext startAccessibilityService()
            }
            logD("accessibility already alive", TAG)
            return@withContext true
        }
        startAccessibilityService()
    }

    private suspend fun hasPrivilege(): Boolean {
        val mode = TweakDataStore.runtimeModeFlow().first()
        return when (mode) {
            RuntimeMode.Root -> runCatching { LibSuX.checkRoot() }.getOrDefault(false)
            RuntimeMode.Shizuku -> ShizukuX.checkShizuku()
            RuntimeMode.Unknow -> false
        }
    }

    private suspend fun readEnabledServicesRaw(): String {
        val raw = ReusableShells.execSync("settings get secure enabled_accessibility_services").trim()
        return if (raw.isEmpty() || raw == "null") "" else raw
    }

    private suspend fun readEnabledServices(): List<String> {
        val raw = readEnabledServicesRaw()
        if (raw.isEmpty()) return emptyList()
        return raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private suspend fun ensureMiuiPermitted(component: String) {
        var miui = ReusableShells.execSync("settings get secure permitted_accessibility_services").trim()
        if (miui == "null") miui = ""
        if (!miui.contains(component)) {
            val miuiNew = if (miui.isNotEmpty()) "$miui:$component" else component
            ReusableShells.execSync(
                "settings put secure permitted_accessibility_services ${shellQuote(miuiNew)}",
            )
            logI("permitted_accessibility_services=$miuiNew", TAG)
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

actual suspend fun ensureAccessibilityServiceEnabled(): Boolean =
    AccessibilityBootstrap.enableIfPrivileged(requireUserPreference = true)
