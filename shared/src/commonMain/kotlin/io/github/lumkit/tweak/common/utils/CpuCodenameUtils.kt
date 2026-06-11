package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.model.AndroidSoc
import kotlinx.serialization.json.Json
import tweak.shared.generated.resources.Res
import java.io.File
import java.io.FileNotFoundException

object CpuCodenameUtils {

    /**
     * 获取设备的CPU代号，例如“SM650”。
     *
     * @return CPU代号，如果无法获取则返回"Unknown"。
     */
    suspend fun getCpuCodename(): String {
        val cacheKey = "hardware1"

        // 检查缓存中是否已经存在CPU代号
        if (Cache.hasKey(cacheKey)) {
            return Cache.get(cacheKey) ?: "Unknown"
        }

        // 尝试从系统属性中获取SoC模型的部分名称
        var socModelPartName = KernelProps.getSystemProp("ro.vendor.soc.model.part_name")
        if (Validator.isValid(socModelPartName)) {
            if (socModelPartName.length > 6) {
                Cache.put(cacheKey, socModelPartName)
                return socModelPartName
            }
        } else {
            // 如果部分名称为空，尝试获取完整的模型名称
            socModelPartName = KernelProps.getSystemProp("ro.vendor.soc.model_name")
        }

        // 如果仍然没有获取到有效的SoC模型名称，尝试获取其他属性
        if (!Validator.isValid(socModelPartName)) {
            val socModel = KernelProps.getSystemProp("ro.soc.model")
            if (Validator.isValid(socModel) && socModel.length > 3) {
                socModelPartName = if (socModel.contains("exynos", ignoreCase = true)) {
                    BOARD
                } else {
                    socModel
                }
            }
        }

        // 读取 /proc/cpuinfo 文件，查找 "Hardware" 或 "Atom(TM)" 行
        val cpuInfoHardwareLine = readCpuInfoHardwareLine()

        // 如果找到了相关的硬件信息，并且包含 ":"
        if (Validator.isValid(cpuInfoHardwareLine) &&
            (cpuInfoHardwareLine.contains("Hardware") || cpuInfoHardwareLine.contains("Atom(TM)")) &&
            cpuInfoHardwareLine.contains(":")
        ) {
            var hardware = extractHardware(cpuInfoHardwareLine)

            // 如果从 /proc/cpuinfo 中提取的硬件信息为空，尝试从其他系统属性中获取
            if (!Validator.isValid(hardware)) {
                hardware = getFallbackHardware()
            }

            // 处理硬件信息中的特定模式
            val processedHardware = processHardwareString(hardware)
            if (Validator.isValid(processedHardware)) {
                Cache.put(cacheKey, processedHardware)
                return processedHardware
            }

            // 最终调整硬件字符串的逻辑
            val adjustedHardware = adjustHardwareString(socModelPartName, hardware)
            Cache.put(cacheKey, adjustedHardware)
            return adjustedHardware
        }

        // 如果 /proc/cpuinfo 中没有相关硬件信息，使用 socModelPartName 作为代号
        var fallbackHardware = ""
        if (Validator.isValid(socModelPartName)) {
            fallbackHardware = socModelPartName.trim()
        }

        // 根据不同条件调整最终的硬件字符串
        fallbackHardware = adjustHardwareString(socModelPartName, fallbackHardware)

        // 缓存并返回
        Cache.put(cacheKey, fallbackHardware)
        return fallbackHardware
    }

    /**
     * 从 /proc/cpuinfo 中读取包含 "Hardware" 或 "Atom(TM)" 的行。
     *
     * @return 找到的硬件信息行，如果未找到则返回"NA"。
     */
    private fun readCpuInfoHardwareLine(): String {
        var hardwareLine = "NA"
        try {
            val file = File("/proc/cpuinfo")
            file.forEachLine { line ->
                if (line.contains("Hardware") || line.contains("Atom(TM)")) {
                    hardwareLine = line
                    return@forEachLine
                }
            }
        } catch (e: FileNotFoundException) {
            // 文件不存在，返回默认值
        }
        return hardwareLine
    }

    /**
     * 从硬件信息行中提取硬件代号。
     *
     * @param hardwareLine 包含硬件信息的行。
     * @return 提取到的硬件代号，如果提取失败则返回空字符串。
     */
    private fun extractHardware(hardwareLine: String): String {
        return try {
            val parts = hardwareLine.split(":")
            if (parts.size >= 2) {
                parts[1].trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 获取备用的硬件信息，如果 /proc/cpuinfo 中没有相关信息。
     *
     * @return 备用的硬件信息。
     */
    private suspend fun getFallbackHardware(): String {
        return try {
            var hardware = KernelProps.getSystemProp("ro.board.platform")
            if (Validator.isValid(hardware)) {
                if (hardware.contains("exynos", ignoreCase = true)) {
                    hardware = BOARD
                }
            } else {
                hardware = BOARD
            }
            hardware
        } catch (e: Exception) {
            e.printStackTrace()
            BOARD
        }
    }

    /**
     * 处理硬件字符串，提取特定模式的代号。
     *
     * @param hardware 原始硬件字符串。
     * @return 处理后的硬件代号，如果未能提取则返回原始字符串。
     */
    private fun processHardwareString(hardware: String): String {
        // 检查是否包含 "(SDM"
        if (hardware.contains("(SDM")) {
            try {
                val start = hardware.indexOf("(") + 1
                val end = hardware.indexOf(")")
                if (start in 1..<end) {
                    val substring = hardware.substring(start, end)
                    if (Validator.isValid(substring)) {
                        Cache.put("hardware1", substring)
                        return substring
                    }
                }
            } catch (e: Exception) {
                // 解析失败，继续
            }
        }

        // 检查是否包含 "SDM"
        if (hardware.contains("SDM")) {
            try {
                val indexOfSDM = hardware.indexOf("SDM")
                var lastIndex = hardware.lastIndexOf(" ")
                if (lastIndex < indexOfSDM) {
                    lastIndex = hardware.length
                }
                val substring2 = hardware.substring(indexOfSDM, lastIndex)
                if (Validator.isValid(substring2)) {
                    Cache.put("hardware1", substring2)
                    return substring2
                }
            } catch (e: Exception) {
                // 解析失败，继续
            }
        }

        if (hardware.contains("Qualcomm Technologies, Inc")) {
            val last = hardware.split("\\s+".toRegex()).last()
            if (last.startsWith("MSM", ignoreCase = true)) {
                val model = last.substring(3)
                if (Validator.isValid(model)) {
                    Cache.put("hardware1", model)
                    return model
                }
            }
        }

        return hardware
    }

    /**
     * 根据不同条件调整硬件字符串。
     *
     * @param socModelPartName SoC模型的部分名称。
     * @param hardware         原始硬件字符串。
     * @return 调整后的硬件字符串。
     */
    private fun adjustHardwareString(socModelPartName: String?, hardware: String): String {
        var adjustedHardware = hardware
        when {
            Validator.isValid(socModelPartName) && Validator.isValid(adjustedHardware) -> {
                adjustedHardware = adjustedHardware.trim()
            }

            SDK_INT > 30 && adjustedHardware.contains("MT") && adjustedHardware.length > 6 -> {
                adjustedHardware = adjustedHardware.trim()
            }

            !adjustedHardware.contains("MT") && socModelPartName != null && adjustedHardware.length > socModelPartName.length -> {
                adjustedHardware = adjustedHardware.trim()
            }

            socModelPartName != null && socModelPartName.length > 3 -> {
                adjustedHardware = socModelPartName.trim()
            }
        }
        return adjustedHardware
    }

    private var map: Map<String, AndroidSoc> = emptyMap()
    private val json = Json {
        isLenient = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun getSocByCpuMode(name: String): AndroidSoc? {
        if (map.isEmpty()) {
            val content = String(Res.readBytes("files/socs.json"))
            map = json.decodeFromString(content)
        }
        return map[name]
    }

    suspend fun getSocByCpuMode(): AndroidSoc? {
        return getSocByCpuMode(getCpuCodename())
    }
}
