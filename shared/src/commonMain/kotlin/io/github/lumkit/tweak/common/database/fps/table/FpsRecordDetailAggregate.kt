package io.github.lumkit.tweak.common.database.fps.table

import io.github.lumkit.tweak.common.utils.AppCpuLoad
import io.github.lumkit.tweak.model.AndroidSoc
import kotlinx.serialization.Serializable

@Serializable
data class FpsRecordDetailAggregate(
    val sessionId: Long,
    val recordDate: String,
    val platformName: String,
    val deviceSoc: AndroidSoc? = null,
    val deviceModel: String,
    val platformVersionName: String,
    val appName: String,
    val appIconPath: String? = null,
    val appVersion: String,
    val appPackageName: String,
    val deviceResolution: String,
    val maxFps: Double,
    val minFps: Double,
    val avgFps: Double,
    val fpsDiff: Double,
    val above45FpsRatio: Double,
    val low5Fps: Double,
    val maxBatteryTemperature: Double,
    val avgPower: Double,
    val fpsSamples: List<Double>,
    val batteryTemperatureSamples: List<Double>,
    val cpuLoadSamples: List<CpuLoadRecord>,
    val gpuLoadSamples: List<Double>,
    val frameTimeSamples: List<Int>,
    val cpuFreqSamples: List<Map<Int, Long>>,
    val cpuTemperatureSamples: List<Double>,
    val cpuCyclesSamples: List<Map<Int, Long>>,
    val gpuFreqSamples: List<Long>,
    val ddrFreqSamples: List<Long>,
    val powerSamples: List<Double>,
    val batteryLevelSamples: List<Double>,
    val threadLoadSamples: List<Map<String, Double>>,
)

@Serializable
data class CpuLoadRecord(
    val app: AppCpuLoad = AppCpuLoad(),
    val device: Map<Int, Double> = emptyMap(),
)
