package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.model.CpuState

class CpuFrequencyUtil {
    private val cpuDir = "/sys/devices/system/cpu/cpu0/"
    private val cpufreqSysDir = "/sys/devices/system/cpu/cpu0/cpufreq/"
    private val scalingMinFreq = "${cpufreqSysDir}scaling_min_freq"
    private val scalingCurFreq = "${cpufreqSysDir}scaling_cur_freq"
    private val scalingMaxFreq = "${cpufreqSysDir}scaling_max_freq"
    private val scalingGovernor = "${cpufreqSysDir}scaling_governor"

    private var platform: String? = null
    private var clusterInfo: List<List<String>>? = null
    private var coreCount: Int = -1

    suspend fun getAvailableFrequencies(cluster: Int): List<String> {
        val clusters = getClusterInfo()
        if (cluster !in clusters.indices) {
            return emptyList()
        }

        val cpu = "cpu${clusters[cluster].first()}"
        val scalingAvailableFrequencies =
            cpufreqSysDir.replace("cpu0", cpu) + "scaling_available_frequencies"
        val legacyClusterTable =
            "/sys/devices/system/cpu/cpufreq/mp-cpufreq/cluster${cluster}_freq_table"

        return when {
            pathExists(scalingAvailableFrequencies) -> splitValues(KernelProps.getProp(scalingAvailableFrequencies))
            pathExists(legacyClusterTable) -> splitValues(KernelProps.getProp(legacyClusterTable))
            else -> emptyList()
        }
    }

    suspend fun getCurrentMaxFrequency(cluster: Int): String {
        val cpu = getClusterCpu(cluster) ?: return ""
        return getCurrentMaxFrequency(cpu)
    }

    suspend fun getCurrentMaxFrequency(core: String): String {
        return KernelProps.getProp(scalingMaxFreq.replace("cpu0", core))
    }

    suspend fun getCurrentFrequency(cluster: Int): String {
        val cpu = getClusterCpu(cluster) ?: return ""
        return getCurrentFrequency(cpu)
    }

    suspend fun getCurrentFrequency(cpu: String): String {
        return getCpuFreqValue(scalingCurFreq.replace("cpu0", cpu))
    }

    suspend fun getCurrentMinFrequency(cluster: Int): String {
        val cpu = getClusterCpu(cluster) ?: return ""
        return getCurrentMinFrequency(cpu)
    }

    suspend fun getCurrentMinFrequency(core: String): String {
        return KernelProps.getProp(scalingMinFreq.replace("cpu0", core))
    }

    suspend fun getAvailableGovernors(cluster: Int): List<String> {
        val cpu = getClusterCpu(cluster) ?: return emptyList()
        val scalingAvailableGovernors =
            cpufreqSysDir.replace("cpu0", cpu) + "scaling_available_governors"
        return splitValues(KernelProps.getProp(scalingAvailableGovernors))
    }

    suspend fun getCurrentScalingGovernor(cluster: Int): String {
        val cpu = getClusterCpu(cluster) ?: return ""
        return getCurrentScalingGovernor(cpu)
    }

    suspend fun getCurrentScalingGovernor(core: String): String {
        return KernelProps.getProp(scalingGovernor.replace("cpu0", core))
    }

    suspend fun getCurrentScalingGovernorParams(cluster: Int): Map<String, String>? {
        val cpu = getClusterCpu(cluster) ?: return null
        val governor = getCurrentScalingGovernor(cpu)
        if (governor.isBlank()) {
            return null
        }
        return mapFileValue(cpuDir.replace("cpu0", cpu) + "cpufreq/$governor")
    }

    suspend fun getCoreGovernorParams(core: Int): Map<String, String>? {
        val cpu = "cpu$core"
        val governor = getCurrentScalingGovernor(cpu)
        if (governor.isBlank()) {
            return null
        }
        return mapFileValue(cpuDir.replace("cpu0", cpu) + "cpufreq/$governor")
    }

    suspend fun setMinFrequency(minFrequency: String, cluster: Int) {
        val clusters = getClusterInfo()
        if (cluster !in clusters.indices || minFrequency.isBlank()) {
            return
        }

        if (isMtk()) {
            KernelProps.exec(
                "echo $cluster $minFrequency > /proc/ppm/policy/hard_userlimit_min_cpu_freq"
            )
            return
        }

        val commands = buildList {
            for (core in clusters[cluster]) {
                val path = scalingMinFreq.replace("cpu0", "cpu$core")
                add("chmod 0664 $path")
                add("echo $minFrequency > $path")
            }
        }
        if (commands.isNotEmpty()) {
            KernelProps.exec(commands)
        }
    }

    suspend fun setMaxFrequency(maxFrequency: String, cluster: Int) {
        val clusters = getClusterInfo()
        if (cluster !in clusters.indices || maxFrequency.isBlank()) {
            return
        }

        if (isMtk()) {
            KernelProps.exec(
                "echo $cluster $maxFrequency > /proc/ppm/policy/hard_userlimit_max_cpu_freq"
            )
            return
        }

        val commands = buildList {
            add("chmod 0664 /sys/module/msm_performance/parameters/cpu_max_freq")
            val values = StringBuilder()
            for (core in clusters[cluster]) {
                val path = scalingMaxFreq.replace("cpu0", "cpu$core")
                add("chmod 0664 $path")
                add("echo $maxFrequency > $path")
                values.append(core).append(':').append(maxFrequency).append(' ')
            }
            add("echo ${values}> /sys/module/msm_performance/parameters/cpu_max_freq")
        }
        KernelProps.exec(commands)
    }

    suspend fun setGovernor(governor: String, cluster: Int) {
        val clusters = getClusterInfo()
        if (cluster !in clusters.indices || governor.isBlank()) {
            return
        }

        val commands = buildList {
            for (core in clusters[cluster]) {
                val path = scalingGovernor.replace("cpu0", "cpu$core")
                add("chmod 0755 $path")
                add("echo $governor > $path")
            }
        }
        if (commands.isNotEmpty()) {
            KernelProps.exec(commands)
        }
    }

    suspend fun getCoreOnlineState(coreIndex: Int): Boolean {
        return KernelProps.getProp(onlinePath(coreIndex)) == "1"
    }

    suspend fun setCoreOnlineState(coreIndex: Int, online: Boolean) {
        val commands = buildList {
            if (exynosCpuhotplugSupport() && getExynosHotplug()) {
                add("echo 0 > /sys/devices/system/cpu/cpuhotplug/enabled;")
            }
            val onlinePath = onlinePath(coreIndex)
            add("chmod 0755 $onlinePath")
            add("echo ${if (online) 1 else 0} > $onlinePath")
        }
        KernelProps.exec(commands)
    }

    suspend fun getExynosHmpUP(): Int {
        return KernelProps.getProp("/sys/kernel/hmp/up_threshold").trim().toIntOrNull() ?: 0
    }

    suspend fun setExynosHmpUP(up: Int) {
        KernelProps.exec(
            "chmod 0664 /sys/kernel/hmp/up_threshold;",
            "echo $up > /sys/kernel/hmp/up_threshold;"
        )
    }

    suspend fun getExynosHmpDown(): Int {
        return KernelProps.getProp("/sys/kernel/hmp/down_threshold").trim().toIntOrNull() ?: 0
    }

    suspend fun setExynosHmpDown(down: Int) {
        KernelProps.exec(
            "chmod 0664 /sys/kernel/hmp/down_threshold;",
            "echo $down > /sys/kernel/hmp/down_threshold;"
        )
    }

    suspend fun getExynosBooster(): Boolean {
        val value = KernelProps.getProp("/sys/kernel/hmp/boost").trim().lowercase()
        return value == "1" || value == "true" || value == "enabled"
    }

    suspend fun setExynosBooster(enabled: Boolean) {
        KernelProps.exec(
            "chmod 0664 /sys/kernel/hmp/boost",
            "echo ${if (enabled) 1 else 0} > /sys/kernel/hmp/boost"
        )
    }

    suspend fun getExynosHotplug(): Boolean {
        val value = KernelProps.getProp("/sys/devices/system/cpu/cpuhotplug/enabled")
            .trim()
            .lowercase()
        return value == "1" || value == "true" || value == "enabled"
    }

    suspend fun setExynosHotplug(enabled: Boolean) {
        KernelProps.exec(
            "chmod 0664 /sys/devices/system/cpu/cpuhotplug/enabled;",
            "echo ${if (enabled) 1 else 0} > /sys/devices/system/cpu/cpuhotplug/enabled;"
        )
    }

    suspend fun getCoreCount(): Int {
        if (coreCount > -1) {
            return coreCount
        }

        var cores = 0
        while (pathExists(cpuDir.replace("cpu0", "cpu$cores"))) {
            cores++
        }
        coreCount = cores
        return cores
    }

    suspend fun getClusterInfo(): List<List<String>> {
        clusterInfo?.let { return it }

        val clusters = mutableListOf<String>()
        var cores = 0
        while (true) {
            val path = "/sys/devices/system/cpu/cpu0/cpufreq/related_cpus".replace("cpu0", "cpu$cores")
            if (!pathExists(path)) {
                break
            }
            val relatedCpus = Files.readText(path).getOrNull().orEmpty().trim()
            if (relatedCpus.isNotEmpty() && relatedCpus !in clusters) {
                clusters.add(relatedCpus)
            }
            cores++
        }

        return clusters
            .map { splitValues(it) }
            .also { clusterInfo = it }
    }

    suspend fun exynosCpuhotplugSupport(): Boolean {
        return pathExists("/sys/devices/system/cpu/cpuhotplug")
    }

    suspend fun exynosHMP(): Boolean {
        return pathExists("/sys/kernel/hmp/down_threshold") &&
            pathExists("/sys/kernel/hmp/up_threshold") &&
            pathExists("/sys/kernel/hmp/boost")
    }

    suspend fun buildShell(cpuStates: List<CpuState>): List<String> {
        return buildList {
            for (cpuState in cpuStates) {
                val cpu = "cpu${cpuState.number}"
                add("chmod 0755 ${onlinePath(cpuState.number)}")
                add("echo ${if (cpuState.enabled) 1 else 0} > ${onlinePath(cpuState.number)}")

                add("chmod 0755 ${scalingGovernor.replace("cpu0", cpu)}")
                add("echo ${cpuState.governor} > ${scalingGovernor.replace("cpu0", cpu)}")

                add("chmod 0664 ${scalingMaxFreq.replace("cpu0", cpu)}")
                add("echo ${cpuState.maxFrequency} > ${scalingMaxFreq.replace("cpu0", cpu)}")

                add("chmod 0664 ${scalingMinFreq.replace("cpu0", cpu)}")
                add("echo ${cpuState.minFrequency} > ${scalingMinFreq.replace("cpu0", cpu)}")
            }
        }
    }

    private suspend fun isMtk(): Boolean {
        if (platform == null) {
            platform = detectPlatform()
        }
        return platform?.startsWith("mt", ignoreCase = true) == true
    }

    private suspend fun detectPlatform(): String {
        return KernelProps.getSystemProp("ro.board.platform")
            .ifBlank { KernelProps.getSystemProp("ro.mediatek.platform") }
            .ifBlank { KernelProps.getSystemProp("ro.hardware") }
            .lowercase()
    }

    private suspend fun getCpuFreqValue(path: String): String {
        val freqValue = Files.readText(path)
            .getOrNull()
            ?.trim()
            ?.toLongOrNull()
            ?: -1L
        return if (freqValue > -1L) freqValue.toString() else ""
    }

    private suspend fun getClusterCpu(cluster: Int): String? {
        val clusters = getClusterInfo()
        if (cluster !in clusters.indices) {
            return null
        }
        return "cpu${clusters[cluster].firstOrNull() ?: return null}"
    }

    private suspend fun pathExists(path: String): Boolean {
        return Files.exists(path).getOrNull() == true
    }

    private suspend fun mapFileValue(directoryPath: String): Map<String, String>? {
        val entries = Files.list(directoryPath).getOrNull() ?: return null
        if (entries.isEmpty()) {
            return emptyMap()
        }

        return buildMap {
            for (entry in entries) {
                val content = Files.readText(entry).getOrNull()?.trim() ?: continue
                put(entry.substringAfterLast('/'), content)
            }
        }
    }

    private fun splitValues(rawValue: String): List<String> {
        return rawValue
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }

    private fun onlinePath(coreIndex: Int): String {
        return "/sys/devices/system/cpu/cpu$coreIndex/online"
    }
}
