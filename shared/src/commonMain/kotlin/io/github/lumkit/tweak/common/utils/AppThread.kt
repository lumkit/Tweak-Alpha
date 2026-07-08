package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

object AppThread {
    private val lastSamples = mutableMapOf<String, ThreadLoadSample>()
    private val pidCache = mutableMapOf<String, Int>()

    suspend fun getThreadLoad(packageName: String): Map<String, Double> {
        if (packageName.isBlank()) {
            return emptyMap()
        }

        val pid = findPid(packageName) ?: return emptyMap<String, Double>().also {
            lastSamples.remove(packageName)
            pidCache.remove(packageName)
        }
        val currentSample = readThreadLoadSample(pid) ?: return emptyMap<String, Double>().also {
            lastSamples.remove(packageName)
            pidCache.remove(packageName)
        }
        val previousSample = lastSamples[packageName]
        if (previousSample == null || previousSample.pid != pid) {
            lastSamples[packageName] = currentSample
            delay(100.milliseconds)
            return getThreadLoad(packageName)
        }

        val totalDelta = currentSample.totalTicks - previousSample.totalTicks
        if (totalDelta <= 0L) {
            lastSamples[packageName] = currentSample
            return emptyMap()
        }

        val loads = mutableMapOf<String, Double>()
        currentSample.threads.forEach { (tid, currentThread) ->
            val previousTicks = previousSample.threads[tid]?.ticks ?: return@forEach
            val threadDelta = currentThread.ticks - previousTicks
            if (threadDelta <= 0L) {
                return@forEach
            }
            loads[currentThread.name] = (loads[currentThread.name] ?: 0.0) +
                threadDelta * 100.0 / totalDelta
        }

        lastSamples[packageName] = currentSample
        return loads
    }

    private suspend fun findPid(packageName: String): Int? {
        pidCache[packageName]?.let { pid ->
            if (Files.exists("/proc/$pid/task").getOrNull() == true) {
                return pid
            }
            pidCache.remove(packageName)
        }

        val output = runCatching {
            ReusableShells.execSync("pidof ${packageName.shellArg()}")
        }.getOrNull().orEmpty()
        val pid = output
            .lineSequence()
            .flatMap { line -> line.trim().split(Regex("\\s+")).asSequence() }
            .firstNotNullOfOrNull { it.toIntOrNull() }
            ?: return null
        pidCache[packageName] = pid
        return pid
    }

    private suspend fun readThreadLoadSample(pid: Int): ThreadLoadSample? {
        val totalTicks = readTotalCpuTicks() ?: return null
        val taskPaths = Files.list("/proc/$pid/task").getOrNull().orEmpty()
        if (taskPaths.isEmpty()) {
            return null
        }

        val threads = buildMap {
            taskPaths.forEach { path ->
                val tid = path.substringAfterLast('/').toIntOrNull() ?: return@forEach
                val stat = Files.readText("/proc/$pid/task/$tid/stat").getOrNull().orEmpty()
                val threadTicks = parseThreadTicks(stat, tid) ?: return@forEach
                put(tid, threadTicks)
            }
        }
        return ThreadLoadSample(pid = pid, totalTicks = totalTicks, threads = threads)
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

    private fun parseThreadTicks(stat: String, tid: Int): ThreadTicks? {
        val nameStartIndex = stat.indexOf('(')
        val endIndex = stat.lastIndexOf(") ")
        if (nameStartIndex < 0 || endIndex <= nameStartIndex) {
            return null
        }
        val name = stat.substring(nameStartIndex + 1, endIndex).takeIf { it.isNotEmpty() }
            ?: tid.toString()
        val fields = stat.substring(endIndex + 2)
            .trim()
            .split(Regex("\\s+"))
        val userTicks = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val systemTicks = fields.getOrNull(12)?.toLongOrNull() ?: return null
        return ThreadTicks(name = name, ticks = userTicks + systemTicks)
    }

    private fun String.shellArg(): String {
        return "'${replace("'", "'\\''")}'"
    }

    private data class ThreadLoadSample(
        val pid: Int,
        val totalTicks: Long,
        val threads: Map<Int, ThreadTicks>,
    )

    private data class ThreadTicks(
        val name: String,
        val ticks: Long,
    )
}
