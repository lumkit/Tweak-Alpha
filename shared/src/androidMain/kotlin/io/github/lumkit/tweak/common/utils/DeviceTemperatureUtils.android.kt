package io.github.lumkit.tweak.common.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.HardwarePropertiesManager
import io.github.lumkit.tweak.application
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal actual object PlatformTemperatureSource {
    private var platformEntries: List<PlatformTemperatureEntry> = emptyList()
    private var platformEntriesMark: TimeMark? = null

    actual suspend fun getTemperatures(category: TemperatureCategory): List<Pair<String, Float>> {
        return getPlatformTemperatureEntries()
            .filter { it.category == category }
            .map { it.name to it.temperature }
    }

    actual suspend fun getBatteryTemperature(): Float? {
        val batteryIntent = application.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return null
        val rawTemperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (rawTemperature == Int.MIN_VALUE) {
            return null
        }
        return rawTemperature / 10f
    }

    private fun getPlatformTemperatureEntries(): List<PlatformTemperatureEntry> {
        val cacheMark = platformEntriesMark
        if (cacheMark != null && cacheMark.elapsedNow() < 2.seconds) {
            return platformEntries
        }

        val hardwarePropertiesManager = application.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE)
            as? HardwarePropertiesManager
        if (hardwarePropertiesManager == null) {
            return platformEntries
        }

        val resolvedEntries = buildList {
            addAll(
                readTemperatures(
                    manager = hardwarePropertiesManager,
                    type = HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    category = TemperatureCategory.CPU,
                    labelPrefix = "cpu",
                )
            )
            addAll(
                readTemperatures(
                    manager = hardwarePropertiesManager,
                    type = HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                    category = TemperatureCategory.GPU,
                    labelPrefix = "gpu",
                )
            )
            addAll(
                readTemperatures(
                    manager = hardwarePropertiesManager,
                    type = HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY,
                    category = TemperatureCategory.BATTERY,
                    labelPrefix = "battery",
                )
            )
        }
        platformEntries = resolvedEntries
        platformEntriesMark = TimeSource.Monotonic.markNow()
        return resolvedEntries
    }

    private fun readTemperatures(
        manager: HardwarePropertiesManager,
        type: Int,
        category: TemperatureCategory,
        labelPrefix: String,
    ): List<PlatformTemperatureEntry> {
        val rawTemperatures = try {
            manager.getDeviceTemperatures(type, HardwarePropertiesManager.TEMPERATURE_CURRENT)
        } catch (_: Throwable) {
            return emptyList()
        }

        if (rawTemperatures.isEmpty()) {
            return emptyList()
        }

        return buildList {
            rawTemperatures.forEachIndexed { index, rawTemperature ->
                if (rawTemperature == HardwarePropertiesManager.UNDEFINED_TEMPERATURE) {
                    return@forEachIndexed
                }
                if (!rawTemperature.isFinite()) {
                    return@forEachIndexed
                }
                val normalizedValue = if (rawTemperature in -20f..125f) {
                    rawTemperature
                } else {
                    normalizeTemperatureValue(rawTemperature)
                } ?: return@forEachIndexed
                if (category == TemperatureCategory.CPU && !isPlausibleDeviceTemperature(normalizedValue)) {
                    return@forEachIndexed
                }
                val displayName = if (rawTemperatures.size == 1) {
                    labelPrefix
                } else {
                    "$labelPrefix$index"
                }
                add(
                    PlatformTemperatureEntry(
                        category = category,
                        name = displayName,
                        temperature = normalizedValue,
                    )
                )
            }
        }
    }

    private data class PlatformTemperatureEntry(
        val category: TemperatureCategory,
        val name: String,
        val temperature: Float,
    )
}
