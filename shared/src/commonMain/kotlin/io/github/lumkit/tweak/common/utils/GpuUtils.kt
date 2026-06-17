package io.github.lumkit.tweak.common.utils

/**
 * GPU 信息与调节工具。
 *
 * 该实现会优先探测当前设备实际暴露的 GPU 节点，再按节点类型选择解析方式，
 */
object GpuUtils {
    private const val ADRENO_ROOT = "/sys/class/kgsl/kgsl-3d0"
    private const val ADRENO_DEVFREQ_ROOT = "$ADRENO_ROOT/devfreq"
    private const val MALI_ROOT = "/sys/class/devfreq/gpufreq"

    private var platformName: String? = null
    private var gpuVendor: GpuVendor? = null
    private var gpuParamsDir: String? = null
    private var gpuLoadPath: String? = null
    private var gpuFrequencySource: FrequencySource? = null
    private var gpuMemorySource: MemorySource? = null

    suspend fun supported(): Boolean {
        return detectGpuVendor() != GpuVendor.UNKNOWN
    }

    suspend fun isAdrenoGpu(): Boolean {
        return detectGpuVendor() == GpuVendor.ADRENO
    }

    suspend fun isMaliGpu(): Boolean {
        return detectGpuVendor() == GpuVendor.MALI
    }

    suspend fun getMemoryUsage(): String? {
        val memoryBytes = when (resolveMemorySource()) {
            MemorySource.None -> null
            MemorySource.KgslPageAlloc -> readRawLong("/sys/devices/virtual/kgsl/kgsl/page_alloc")
            MemorySource.MaliProc -> parseMaliMemoryUsage(
                Files.readText("/proc/mali/memory_usage").getOrNull().orEmpty()
            )
        } ?: return null

        if (memoryBytes < 0L) {
            return null
        }
        return memoryBytes.formatMemorySize()
    }

    private var gles = ""
    suspend fun gles(): String {
        return gles.ifEmpty {
            PlatformGpuInfo.gles().also {
                gles = it
            }
        }
    }

    suspend fun getGpuFreq(): String {
        return when (val source = resolveFrequencySource()) {
            FrequencySource.None -> ""
            is FrequencySource.Direct -> {
                val rawValue = readFirstNumericToken(source.path) ?: return ""
                normalizeFrequencyToMhz(rawValue).toString()
            }

            is FrequencySource.Tokenized -> {
                val line = Files.readText(source.path).getOrNull().orEmpty()
                val tokenValue = line
                    .trim()
                    .split(Regex("\\s+"))
                    .getOrNull(source.tokenIndex)
                    ?.filter { it.isDigit() }
                    ?.toLongOrNull()
                    ?: return ""
                normalizeTokenizedFrequency(tokenValue).toString()
            }
        }
    }

    suspend fun getGpuLoad(): Int {
        val path = resolveGpuLoadPath() ?: return -1
        val rawValue = Files.readText(path).getOrNull().orEmpty()
        return rawValue
            .replace("%", " ")
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toIntOrNull()
            ?: -1
    }

    suspend fun getAvailableFreqs(): List<String> {
        return splitValues(readGpuNode("available_frequencies"))
    }

    suspend fun getFreqTableMhz(): List<String> {
        if (!isAdrenoGpu()) {
            return emptyList()
        }
        return splitValues(KernelProps.getProp("$ADRENO_ROOT/freq_table_mhz"))
    }

    suspend fun getGovernors(): List<String> {
        return splitValues(readGpuNode("available_governors"))
    }

    suspend fun getMinFreq(): String {
        return readGpuNode("min_freq")
    }

    suspend fun setMinFreq(value: String) {
        writeGpuNode("min_freq", value)
    }

    suspend fun getMaxFreq(): String {
        return readGpuNode("max_freq")
    }

    suspend fun setMaxFreq(value: String) {
        writeGpuNode("max_freq", value)
    }

    suspend fun getGovernor(): String {
        return readGpuNode("governor")
    }

    suspend fun setGovernor(value: String) {
        writeGpuNode("governor", value)
    }

    suspend fun getAdrenoGpuMinPowerLevel(): String {
        return readText("$ADRENO_ROOT/min_pwrlevel")
    }

    suspend fun setAdrenoGpuMinPowerLevel(value: String) {
        writeNode("$ADRENO_ROOT/min_pwrlevel", value)
    }

    suspend fun getAdrenoGpuMaxPowerLevel(): String {
        return readText("$ADRENO_ROOT/max_pwrlevel")
    }

    suspend fun setAdrenoGpuMaxPowerLevel(value: String) {
        writeNode("$ADRENO_ROOT/max_pwrlevel", value)
    }

    suspend fun getAdrenoGpuDefaultPowerLevel(): String {
        return readText("$ADRENO_ROOT/default_pwrlevel")
    }

    suspend fun setAdrenoGpuDefaultPowerLevel(value: String) {
        writeNode("$ADRENO_ROOT/default_pwrlevel", value)
    }

    suspend fun getAdrenoGpuPowerLevels(): List<String> {
        val count = readText("$ADRENO_ROOT/num_pwrlevels").toIntOrNull() ?: return emptyList()
        return List(count) { index -> index.toString() }
    }

    private suspend fun detectGpuVendor(): GpuVendor {
        gpuVendor?.let { return it }
        val resolvedVendor = when {
            pathExists(ADRENO_ROOT) -> GpuVendor.ADRENO
            pathExists(MALI_ROOT) -> GpuVendor.MALI
            else -> GpuVendor.UNKNOWN
        }
        gpuVendor = resolvedVendor
        return resolvedVendor
    }

    private suspend fun getGpuParamsDir(): String {
        gpuParamsDir?.let { return it }
        val resolvedPath = when (detectGpuVendor()) {
            GpuVendor.ADRENO -> ADRENO_DEVFREQ_ROOT
            GpuVendor.MALI -> MALI_ROOT
            GpuVendor.UNKNOWN -> ""
        }
        gpuParamsDir = resolvedPath
        return resolvedPath
    }

    private suspend fun resolveGpuLoadPath(): String? {
        gpuLoadPath?.let { return it.ifBlank { null } }

        val candidates = listOf(
            "/sys/kernel/gpu/gpu_busy",
            "$ADRENO_DEVFREQ_ROOT/gpu_load",
            "$ADRENO_ROOT/gpu_busy_percentage",
            "$ADRENO_ROOT/gpuload",
            "$MALI_ROOT/mali_ondemand/utilisation",
            "/sys/kernel/debug/ged/hal/gpu_utilization",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
        )

        val resolvedPath = candidates.firstOrNull { pathExists(it) }.orEmpty()
        gpuLoadPath = resolvedPath
        return resolvedPath.ifBlank { null }
    }

    private suspend fun resolveFrequencySource(): FrequencySource {
        gpuFrequencySource?.let { return it }

        val paramsDir = getGpuParamsDir()
        val resolvedSource = when {
            paramsDir.isNotBlank() && pathExists("$paramsDir/cur_freq") ->
                FrequencySource.Direct("$paramsDir/cur_freq")

            pathExists("/sys/kernel/gpu/gpu_clock") ->
                FrequencySource.Direct("/sys/kernel/gpu/gpu_clock")

            pathExists("/sys/kernel/debug/ged/hal/current_freqency") ->
                FrequencySource.Tokenized("/sys/kernel/debug/ged/hal/current_freqency", tokenIndex = 1)

            pathExists("/sys/kernel/ged/hal/current_freqency") ->
                FrequencySource.Tokenized("/sys/kernel/ged/hal/current_freqency", tokenIndex = 1)

            else -> FrequencySource.None
        }

        gpuFrequencySource = resolvedSource
        return resolvedSource
    }

    private suspend fun resolveMemorySource(): MemorySource {
        gpuMemorySource?.let { return it }

        val resolvedSource = when {
            isMtkPlatform() && pathExists("/proc/mali/memory_usage") -> MemorySource.MaliProc
            pathExists("/sys/devices/virtual/kgsl/kgsl/page_alloc") -> MemorySource.KgslPageAlloc
            else -> MemorySource.None
        }

        gpuMemorySource = resolvedSource
        return resolvedSource
    }

    private suspend fun isMtkPlatform(): Boolean {
        val platform = platformName ?: detectPlatform().also { platformName = it }
        return platform.startsWith("mt", ignoreCase = true)
    }

    private suspend fun detectPlatform(): String {
        return KernelProps.getSystemProp("ro.board.platform")
            .ifBlank { KernelProps.getSystemProp("ro.mediatek.platform") }
            .ifBlank { KernelProps.getSystemProp("ro.hardware") }
            .lowercase()
    }

    private suspend fun readGpuNode(name: String): String {
        val paramsDir = getGpuParamsDir()
        if (paramsDir.isBlank()) {
            return ""
        }
        return readText("$paramsDir/$name")
    }

    private suspend fun writeGpuNode(name: String, value: String) {
        val paramsDir = getGpuParamsDir()
        if (paramsDir.isBlank()) {
            return
        }
        writeNode("$paramsDir/$name", value)
    }

    private suspend fun writeNode(path: String, value: String, mode: String = "0664") {
        if (value.isBlank() || !pathExists(path)) {
            return
        }
        Files.chmod(path, mode)
        val normalizedValue = if (value.endsWith('\n')) value else "$value\n"
        Files.writeText(path, normalizedValue)
    }

    private suspend fun pathExists(path: String): Boolean {
        return Files.exists(path).getOrNull() == true
    }

    private suspend fun readText(path: String): String {
        return Files.readText(path).getOrNull().orEmpty().trim()
    }

    private suspend fun readRawLong(path: String): Long? {
        return readText(path).toLongOrNull()
    }

    private suspend fun readFirstNumericToken(path: String): Long? {
        val content = Files.readText(path).getOrNull().orEmpty()
        return Regex("""-?\d+""").find(content)?.value?.toLongOrNull()
    }

    private fun parseMaliMemoryUsage(content: String): Long? {
        val totalLine = content.lineSequence().firstOrNull { line ->
            line.contains("Total", ignoreCase = true)
        } ?: return null

        val parenthesizedValue = Regex("""\((\d+)""").find(totalLine)?.groupValues?.getOrNull(1)
        if (parenthesizedValue != null) {
            return parenthesizedValue.toLongOrNull()
        }
        return Regex("""\d+""").find(totalLine)?.value?.toLongOrNull()
    }

    fun normalizeFrequencyToMhz(rawValue: Long): Long {
        return when {
            rawValue >= 1_000_000L -> rawValue / 1_000_000L
            rawValue >= 10_000L -> rawValue / 1_000L
            else -> rawValue
        }
    }

    private fun normalizeTokenizedFrequency(rawValue: Long): Long {
        return when {
            rawValue >= 1_000_000L -> rawValue / 1_000L
            rawValue >= 10_000L -> rawValue / 1_000L
            else -> rawValue
        }
    }

    private fun splitValues(rawValue: String): List<String> {
        return rawValue
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }

    private enum class GpuVendor {
        ADRENO,
        MALI,
        UNKNOWN,
    }

    private sealed interface FrequencySource {
        data object None : FrequencySource
        data class Direct(val path: String) : FrequencySource
        data class Tokenized(val path: String, val tokenIndex: Int) : FrequencySource
    }

    private enum class MemorySource {
        None,
        KgslPageAlloc,
        MaliProc,
    }
}
