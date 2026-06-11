package io.github.lumkit.tweak.common.utils

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * CPU 负载计算工具。
 *
 * 通过读取 `/proc/stat` 两次采样值，并比较两次采样间的总时钟差值与 idle 时钟差值，
 * 计算整机和每个核心的瞬时负载率。
 */
class CpuLoadUtils {
    companion object {
        private var lastCpuState: String = ""
        private var lastCpuStateMap: Map<Int, Double>? = null
        private var lastCpuStateSum: String = ""
        private var lastCpuStateMark: TimeMark? = null
    }

    suspend fun getCpuLoad(): Map<Int, Double> {
        val cached = lastCpuStateMap
        if (cached != null && lastCpuStateMark?.elapsedNow()?.let { it < 500.milliseconds } == true) {
            return cached
        }

        val loads = mutableMapOf<Int, Double>()
        val times = readCpuStatLines(prefix = "cpu")
        if (times.isBlank() || !times.startsWith("cpu")) {
            return loads
        }

        return try {
            if (lastCpuState.isBlank()) {
                lastCpuState = times
                delay(100.milliseconds)
                getCpuLoad()
            } else {
                val currentTicks = times.lines().filter { it.isNotBlank() }
                val previousTicks = lastCpuState.lines().filter { it.isNotBlank() }

                for (cpuCurrentTime in currentTicks) {
                    val currentColumns = cpuCurrentTime.normalizedColumns()
                    val previousColumns = previousTicks
                        .firstOrNull { it.startsWith("${currentColumns[0]} ") }
                        ?.normalizedColumns()

                    if (previousColumns != null) {
                        val total1 = cpuTotalTime(currentColumns)
                        val idle1 = cpuIdleTime(currentColumns)
                        val total0 = cpuTotalTime(previousColumns)
                        val idle0 = cpuIdleTime(previousColumns)
                        val timeDiff = total1 - total0

                        loads[getCpuIndex(currentColumns)] = when {
                            timeDiff == 0L -> 0.0
                            idle1 - idle0 < 1L -> 100.0
                            else -> 100 - ((idle1 - idle0) * 100.0 / timeDiff)
                        }
                    } else {
                        loads[getCpuIndex(currentColumns)] = 0.0
                    }
                }

                lastCpuState = times
                lastCpuStateMark = TimeSource.Monotonic.markNow()
                lastCpuStateMap = loads
                loads
            }
        } catch (_: Throwable) {
            loads
        }
    }

    suspend fun getCpuLoadSum(): Double {
        val cached = lastCpuStateMap
        if (
            cached != null &&
            lastCpuStateMark?.elapsedNow()?.let { it < 500.milliseconds } == true &&
            cached.containsKey(-1)
        ) {
            return cached.getValue(-1)
        }

        val times = readCpuStatLines(prefix = "cpu ")
        if (times.isBlank() || !times.startsWith("cpu")) {
            return -1.0
        }

        return try {
            if (lastCpuStateSum.isBlank()) {
                lastCpuStateSum = times
                delay(100.milliseconds)
                getCpuLoadSum()
            } else {
                val currentTicks = times.lines().filter { it.isNotBlank() }
                val previousTicks = lastCpuStateSum.lines().filter { it.isNotBlank() }

                for (cpuCurrentTime in currentTicks) {
                    val currentColumns = cpuCurrentTime.normalizedColumns()
                    if (currentColumns[0] == "cpu") {
                        val previousColumns = previousTicks
                            .firstOrNull { it.startsWith("cpu ") }
                            ?.normalizedColumns()
                            ?: return 0.0

                        lastCpuStateSum = times
                        val total1 = cpuTotalTime(currentColumns)
                        val idle1 = cpuIdleTime(currentColumns)
                        val total0 = cpuTotalTime(previousColumns)
                        val idle0 = cpuIdleTime(previousColumns)
                        val timeDiff = total1 - total0

                        return when {
                            timeDiff == 0L -> 0.0
                            idle1 - idle0 < 1L -> 100.0
                            else -> 100 - ((idle1 - idle0) * 100.0 / timeDiff)
                        }
                    }
                }
                0.0
            }
        } catch (_: Throwable) {
            -1.0
        }
    }

    private suspend fun readCpuStatLines(prefix: String): String {
        val content = Files.readText("/proc/stat").getOrNull().orEmpty()
        if (content.isBlank()) {
            return ""
        }
        return content.lineSequence()
            .filter { line -> line.startsWith(prefix) }
            .joinToString(separator = "\n")
            .trim()
    }

    private fun getCpuIndex(columns: List<String>): Int {
        return if (columns.first() == "cpu") {
            -1
        } else {
            columns.first().removePrefix("cpu").toInt()
        }
    }

    private fun cpuTotalTime(columns: List<String>): Long {
        return columns.drop(1).sumOf { it.toLong() }
    }

    private fun cpuIdleTime(columns: List<String>): Long {
        return columns[4].toLong()
    }

    private fun String.normalizedColumns(): List<String> {
        return trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
}
