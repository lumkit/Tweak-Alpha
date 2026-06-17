package io.github.lumkit.tweak.common.utils

internal enum class TemperatureCategory {
    CPU,
    GPU,
    BATTERY,
}

internal expect object PlatformTemperatureSource {
    suspend fun getTemperatures(category: TemperatureCategory): List<Pair<String, Float>>

    suspend fun getBatteryTemperature(): Float?
}

/**
 * 设备温度读取工具。
 *
 * 这一层使用“多来源聚合”策略提升机型兼容性：
 * - thermal zone：兼容大多数 Android 内核
 * - hwmon：补充部分内核只暴露在 hwmon 下的温度节点
 * - power_supply：优先处理电池温度
 * - Android 平台来源：由 [PlatformTemperatureSource] 提供电池广播和硬件温度 API 兜底
 *
 * 仍需说明的是，Android 不存在能覆盖全部厂商全部机型的统一公开温度 API。当前实现已经尽量
 * 聚合常见来源，但如果某些 ROM 完全不暴露 CPU/GPU 温度节点，依然可能无法读到。
 *
 * 所有公开温度值都使用摄氏度 [Float]，不带单位符号。
 * 集合类型返回值使用 [Pair] 表示：
 * - `first` 保存传感器名称
 * - `second` 保存具体温度值
 */
object DeviceTemperatureUtils {
    private const val thermalRoot = "/sys/class/thermal"
    private const val hwmonRoot = "/sys/class/hwmon"
    private const val powerSupplyRoot = "/sys/class/power_supply"

    private val cpuKeywords = listOf(
        "cpu",
        "cpuss",
        "cluster",
        "silver",
        "gold",
        "prime",
        "big",
        "little",
        "soc",
        "ap",
        "application",
        "x1",
        "a53",
        "a55",
        "a57",
        "a73",
        "a75",
        "a76",
        "a78",
    )

    private val gpuKeywords = listOf(
        "gpu",
        "gpuss",
        "kgsl",
        "adreno",
        "mali",
        "g3d",
        "3d",
    )

    private val batteryKeywords = listOf(
        "battery",
        "bat",
        "batt",
        "bms",
        "maxfg",
        "fg",
    )

    private val batteryTempPaths = listOf(
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/Battery/temp",
        "/sys/class/power_supply/bms/temp",
        "/sys/class/power_supply/maxfg/temp",
        "/sys/devices/platform/battery/temperature",
        "/sys/devices/platform/battery/temp",
    )

    /**
     * 获取 CPU 温度集合。
     */
    suspend fun getCpuTemperatures(): List<Pair<String, Float>> {
        return collectTemperatures(TemperatureCategory.CPU)
    }

    /**
     * 获取 GPU 温度集合。
     */
    suspend fun getGpuTemperatures(): List<Pair<String, Float>> {
        return collectTemperatures(TemperatureCategory.GPU)
    }

    /**
     * 获取电池温度。
     *
     * 读取顺序：
     * 1. Android 电池广播
     * 2. 常见 power_supply/battery 节点
     * 3. 扫描 power_supply 目录内所有可能的电池温度节点
     * 4. thermal zone / hwmon / 平台温度来源的 battery 项
     */
    suspend fun getBatteryTemperature(): Float? {
        PlatformTemperatureSource.getBatteryTemperature()?.let { return it }

        batteryTempPaths.forEach { path ->
            readTemperature(path)?.let { return it }
        }

        selectBestBatteryTemperature(getPowerSupplyBatteryTemperatures())?.let { return it.second }

        return mergeTemperatureEntries(
            getThermalZoneTemperatures(batteryKeywords),
            getHwmonTemperatures(TemperatureCategory.BATTERY, batteryKeywords),
            PlatformTemperatureSource.getTemperatures(TemperatureCategory.BATTERY),
        ).averageTemperatureOrNull()
    }

    /**
     * 获取 CPU 平均温度。
     */
    suspend fun getAverageCpuTemperature(): Float? {
        return getCpuTemperatures().averageTemperatureOrNull()
    }

    /**
     * 获取 GPU 平均温度。
     */
    suspend fun getAverageGpuTemperature(): Float? {
        return getGpuTemperatures().averageTemperatureOrNull()
    }

    private suspend fun collectTemperatures(category: TemperatureCategory): List<Pair<String, Float>> {
        val keywords = category.keywords()
        return mergeTemperatureEntries(
            getThermalZoneTemperatures(keywords),
            getHwmonTemperatures(category, keywords),
            PlatformTemperatureSource.getTemperatures(category),
        )
    }

    private suspend fun getThermalZoneTemperatures(keywords: List<String>): List<Pair<String, Float>> {
        val zones = listDirectoryOrEmpty(thermalRoot)
            .filter { path -> path.substringAfterLast('/').startsWith("thermal_zone") }

        if (zones.isEmpty()) {
            return emptyList()
        }

        return buildList {
            zones.forEach { zonePath ->
                val type = Files.readText("$zonePath/type")
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                if (!matchesKeywords(type, keywords)) {
                    return@forEach
                }
                val temperature = readTemperature("$zonePath/temp") ?: return@forEach
                add(type.ifBlank { zonePath.substringAfterLast('/') } to temperature)
            }
        }
    }

    private suspend fun getHwmonTemperatures(
        category: TemperatureCategory,
        keywords: List<String>,
    ): List<Pair<String, Float>> {
        val hwmonDirectories = listDirectoryOrEmpty(hwmonRoot)
            .filter { path -> path.substringAfterLast('/').startsWith("hwmon") }
        if (hwmonDirectories.isEmpty()) {
            return emptyList()
        }

        return buildList {
            hwmonDirectories.forEach { directory ->
                val entries = listDirectoryOrEmpty(directory)
                if (entries.isEmpty()) {
                    return@forEach
                }
                val hwmonName = Files.readText("$directory/name")
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                entries
                    .filter { it.substringAfterLast('/').matches(Regex("""temp\d+_input""")) }
                    .forEach { inputPath ->
                        val inputName = inputPath.substringAfterLast('/')
                        val sensorIndex = inputName
                            .removePrefix("temp")
                            .substringBefore("_")
                        val label = Files.readText("$directory/temp${sensorIndex}_label")
                            .getOrNull()
                            ?.trim()
                            .orEmpty()
                        val composedName = buildString {
                            append(label)
                            append(' ')
                            append(hwmonName)
                            append(' ')
                            append(inputName)
                        }.trim()
                        if (!matchesKeywords(composedName, keywords)) {
                            return@forEach
                        }
                        val temperature = readTemperature(inputPath) ?: return@forEach
                        val displayName = buildDisplayName(
                            preferred = label,
                            fallback = hwmonName.ifBlank { inputName },
                            category = category,
                        )
                        add(displayName to temperature)
                    }
            }
        }
    }

    private suspend fun getPowerSupplyBatteryTemperatures(): List<Pair<String, Float>> {
        val supplyDirectories = listDirectoryOrEmpty(powerSupplyRoot)
        if (supplyDirectories.isEmpty()) {
            return emptyList()
        }

        return buildList {
            supplyDirectories.forEach { directory ->
                val name = directory.substringAfterLast('/')
                val type = Files.readText("$directory/type")
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                val matchText = "$name $type"
                if (!matchesKeywords(matchText, batteryKeywords)) {
                    return@forEach
                }

                readTemperature("$directory/temp")
                    ?.let { add(name to it) }
                    ?: readTemperature("$directory/temperature")
                        ?.let { add(name to it) }
            }
        }
    }

    private suspend fun listDirectoryOrEmpty(path: String): List<String> {
        return Files.list(path).getOrNull().orEmpty()
    }

    private suspend fun readTemperature(path: String): Float? {
        val rawValue = Files.readText(path)
            .getOrNull()
            ?.trim()
            ?.substringBefore('\n')
            ?: return null
        return parseTemperature(rawValue)
    }

    private fun parseTemperature(rawValue: String): Float? {
        val numericValue = rawValue.toFloatOrNull() ?: return null
        return normalizeTemperatureValue(numericValue)
    }

    private fun matchesKeywords(text: String, keywords: List<String>): Boolean {
        val normalizedText = text.trim().lowercase()
        if (normalizedText.isBlank()) {
            return false
        }
        return keywords.any { keyword -> keyword in normalizedText }
    }

    private fun mergeTemperatureEntries(vararg sources: List<Pair<String, Float>>): List<Pair<String, Float>> {
        val merged = LinkedHashMap<String, Pair<String, Float>>()
        sources.forEach { source ->
            source.forEach { (name, value) ->
                val normalizedName = normalizeSensorName(name)
                if (normalizedName.isBlank()) {
                    return@forEach
                }
                merged.putIfAbsent(normalizedName, name.trim() to value)
            }
        }
        return merged.values.toList()
    }

    private fun normalizeSensorName(name: String): String {
        return name.lowercase()
            .replace(Regex("""[\s_\-:/]+"""), "")
            .trim()
    }

    private fun buildDisplayName(
        preferred: String,
        fallback: String,
        category: TemperatureCategory,
    ): String {
        return preferred.ifBlank {
            fallback.ifBlank {
                category.name.lowercase()
            }
        }
    }

    private fun selectBestBatteryTemperature(entries: List<Pair<String, Float>>): Pair<String, Float>? {
        if (entries.isEmpty()) {
            return null
        }
        return entries.minByOrNull { (name, _) ->
            when {
                name.equals("battery", ignoreCase = true) -> 0
                "battery" in name.lowercase() -> 1
                "bms" in name.lowercase() -> 2
                "maxfg" in name.lowercase() -> 3
                else -> 4
            }
        }
    }

    private fun TemperatureCategory.keywords(): List<String> {
        return when (this) {
            TemperatureCategory.CPU -> cpuKeywords
            TemperatureCategory.GPU -> gpuKeywords
            TemperatureCategory.BATTERY -> batteryKeywords
        }
    }

    private fun List<Pair<String, Float>>.averageTemperatureOrNull(): Float? {
        if (isEmpty()) {
            return null
        }
        val nonZeroEntries = filter { it.second != 0f }
        if (nonZeroEntries.isEmpty()) {
            return 0f
        }
        return nonZeroEntries.sumOf { it.second.toDouble() }.toFloat() / nonZeroEntries.size
    }
}

internal fun normalizeTemperatureValue(value: Float): Float? {
    if (!value.isFinite()) {
        return null
    }
    return when {
        value >= 1000f -> value / 1000f
        value >= 200f -> value / 10f
        value <= -1000f -> value / 1000f
        value <= -200f -> value / 10f
        else -> value
    }
}
