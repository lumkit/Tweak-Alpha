package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
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
        private val appPidCache = mutableMapOf<String, Int>()
        private val lastAppCpuSamples = mutableMapOf<String, AppCpuSample>()
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

    suspend fun getAppCpuLoad(packageName: String): AppCpuLoad {
        if (packageName.isBlank()) {
            return AppCpuLoad()
        }

        val pid = findPid(packageName) ?: return AppCpuLoad().also {
            lastAppCpuSamples.remove(packageName)
            appPidCache.remove(packageName)
        }
        val currentSample = readAppCpuSample(pid) ?: return AppCpuLoad().also {
            lastAppCpuSamples.remove(packageName)
            appPidCache.remove(packageName)
        }
        val previousSample = lastAppCpuSamples[packageName]
        if (previousSample == null || previousSample.pid != pid) {
            lastAppCpuSamples[packageName] = currentSample
            delay(100.milliseconds)
            return getAppCpuLoad(packageName)
        }

        val totalDelta = currentSample.totalTicks - previousSample.totalTicks
        val processDelta = currentSample.processTicks - previousSample.processTicks
        lastAppCpuSamples[packageName] = currentSample
        if (totalDelta <= 0L || processDelta <= 0L) {
            return AppCpuLoad()
        }
        return AppCpuLoad(
            total = processDelta * 100.0 / totalDelta,
            cores = currentSample.threadTicks
                .mapNotNull { (tid, currentThread) ->
                    val previousThreadTicks = previousSample.threadTicks[tid]?.ticks ?: return@mapNotNull null
                    val threadDelta = currentThread.ticks - previousThreadTicks
                    if (threadDelta <= 0L) {
                        return@mapNotNull null
                    }
                    currentThread.processor to threadDelta * 100.0 / totalDelta
                }
                .groupingBy { it.first }
                .fold(0.0) { accumulator, item -> accumulator + item.second }
        )
    }

    suspend fun isAppRunning(packageName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        return findPid(packageName) != null
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

    private suspend fun findPid(packageName: String): Int? {
        appPidCache[packageName]?.let { pid ->
            if (Files.exists("/proc/$pid/stat").getOrNull() == true) {
                return pid
            }
            appPidCache.remove(packageName)
        }

        val output = runCatching {
            ReusableShells.execSync("pidof ${packageName.shellArg()}")
        }.getOrNull().orEmpty()
        val pid = output
            .lineSequence()
            .flatMap { line -> line.trim().split(Regex("\\s+")).asSequence() }
            .firstNotNullOfOrNull { it.toIntOrNull() }
            ?: return null
        appPidCache[packageName] = pid
        return pid
    }

    private suspend fun readAppCpuSample(pid: Int): AppCpuSample? {
        val totalTicks = readTotalCpuTicks() ?: return null
        val processStat = Files.readText("/proc/$pid/stat").getOrNull().orEmpty()
        val processTicks = parseProcessCpuTicks(processStat) ?: return null
        val threadTicks = readThreadCpuTicks(pid)
        return AppCpuSample(
            pid = pid,
            totalTicks = totalTicks,
            processTicks = processTicks,
            threadTicks = threadTicks,
        )
    }

    private suspend fun readThreadCpuTicks(pid: Int): Map<Int, ThreadCpuTicks> {
        val taskPaths = Files.list("/proc/$pid/task").getOrNull().orEmpty()
        if (taskPaths.isEmpty()) {
            return emptyMap()
        }
        return buildMap {
            taskPaths.forEach { path ->
                val tid = path.substringAfterLast('/').toIntOrNull() ?: return@forEach
                val stat = Files.readText("/proc/$pid/task/$tid/stat").getOrNull().orEmpty()
                val ticks = parseThreadCpuTicks(stat) ?: return@forEach
                put(tid, ticks)
            }
        }
    }

    private suspend fun readTotalCpuTicks(): Long? {
        val line = Files.readText("/proc/stat")
            .getOrNull()
            .orEmpty()
            .lineSequence()
            .firstOrNull { it.startsWith("cpu ") }
            ?: return null
        val values = line.trim().split(Regex("\\s+")).drop(1)
        if (values.isEmpty()) {
            return null
        }
        return values.sumOf { it.toLongOrNull() ?: 0L }
    }

    private fun parseProcessCpuTicks(stat: String): Long? {
        val endIndex = stat.lastIndexOf(") ")
        if (endIndex < 0) {
            return null
        }
        val fields = stat.substring(endIndex + 2)
            .trim()
            .split(Regex("\\s+"))
        val userTicks = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val systemTicks = fields.getOrNull(12)?.toLongOrNull() ?: return null
        val childrenUserTicks = fields.getOrNull(13)?.toLongOrNull() ?: 0L
        val childrenSystemTicks = fields.getOrNull(14)?.toLongOrNull() ?: 0L
        return userTicks + systemTicks + childrenUserTicks + childrenSystemTicks
    }

    private fun parseThreadCpuTicks(stat: String): ThreadCpuTicks? {
        val endIndex = stat.lastIndexOf(") ")
        if (endIndex < 0) {
            return null
        }
        val fields = stat.substring(endIndex + 2)
            .trim()
            .split(Regex("\\s+"))
        val userTicks = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val systemTicks = fields.getOrNull(12)?.toLongOrNull() ?: return null
        val processor = fields.getOrNull(36)?.toIntOrNull() ?: return null
        return ThreadCpuTicks(
            processor = processor,
            ticks = userTicks + systemTicks,
        )
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

    private fun String.shellArg(): String {
        return "'${replace("'", "'\\''")}'"
    }

    private data class AppCpuSample(
        val pid: Int,
        val totalTicks: Long,
        val processTicks: Long,
        val threadTicks: Map<Int, ThreadCpuTicks>,
    )

    private data class ThreadCpuTicks(
        val processor: Int,
        val ticks: Long,
    )
}

@Serializable
data class AppCpuLoad(
    val total: Double = 0.0,
    val cores: Map<Int, Double> = emptyMap(),
)
