package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.utils.DeviceMemoryInfoUtils.getMemoryInfo
import kotlinx.serialization.Serializable

/**
 * 设备内存信息模型。
 *
 * 大部分字段都来自 `/proc/meminfo`，并统一转换为 Byte。
 * 某些内核字段并非所有设备都存在，因此所有属性都提供 `0L` 默认值。
 *
 * 需要额外说明的是，`HugePages_*` 这几项在 Linux 中表示页数量而不是内存大小，
 * 因此这里保留原始计数值，并以 `Count` 后缀区分。
 */
@Serializable
data class DeviceMemoryInfoModel(
    val memTotal: Long = 0L,
    val memFree: Long = 0L,
    val memAvailable: Long = 0L,
    val buffers: Long = 0L,
    val cached: Long = 0L,
    val swapCached: Long = 0L,
    val active: Long = 0L,
    val inactive: Long = 0L,
    val activeAnon: Long = 0L,
    val inactiveAnon: Long = 0L,
    val activeFile: Long = 0L,
    val inactiveFile: Long = 0L,
    val unevictable: Long = 0L,
    val mlocked: Long = 0L,
    val swapTotal: Long = 0L,
    val swapFree: Long = 0L,
    val zswap: Long = 0L,
    val zswapped: Long = 0L,
    val dirty: Long = 0L,
    val writeback: Long = 0L,
    val anonPages: Long = 0L,
    val mapped: Long = 0L,
    val shmem: Long = 0L,
    val kReclaimable: Long = 0L,
    val slab: Long = 0L,
    val sReclaimable: Long = 0L,
    val sUnreclaim: Long = 0L,
    val kernelStack: Long = 0L,
    val shadowCallStack: Long = 0L,
    val pageTables: Long = 0L,
    val secPageTables: Long = 0L,
    val nfsUnstable: Long = 0L,
    val bounce: Long = 0L,
    val writebackTmp: Long = 0L,
    val commitLimit: Long = 0L,
    val committedAs: Long = 0L,
    val vmallocTotal: Long = 0L,
    val vmallocUsed: Long = 0L,
    val vmallocChunk: Long = 0L,
    val perCpu: Long = 0L,
    val hardwareCorrupted: Long = 0L,
    val anonHugePages: Long = 0L,
    val shmemHugePages: Long = 0L,
    val shmemPmdMapped: Long = 0L,
    val fileHugePages: Long = 0L,
    val filePmdMapped: Long = 0L,
    val cmaTotal: Long = 0L,
    val cmaFree: Long = 0L,
    val hugePageSize: Long = 0L,
    val hugeTlb: Long = 0L,
    val directMap4k: Long = 0L,
    val directMap2M: Long = 0L,
    val directMap4M: Long = 0L,
    val directMap1G: Long = 0L,
    val lazyFree: Long = 0L,
    val hugePagesTotalCount: Long = 0L,
    val hugePagesFreeCount: Long = 0L,
    val hugePagesRsvdCount: Long = 0L,
    val hugePagesSurpCount: Long = 0L,
)

/**
 * 设备内存信息工具。
 *
 * 每次调用 [getMemoryInfo] 都会重新读取 `/proc/meminfo`，并返回一个已经完成单位
 * 转换的 [DeviceMemoryInfoModel]。
 */
object DeviceMemoryInfoUtils {
    private const val memInfoPath = "/proc/meminfo"
    private const val devfreqRoot = "/sys/class/devfreq"
    private val zramDeviceName = Regex("zram\\d+")
    private val activeCompAlgorithm = Regex("\\[([^\\]]+)]")

    private val memoryFreqKeywords = listOf(
        "ddr",
        "dram",
        "bimc",
        "mif",
        "llcc",
        "memlat",
        "cpu-bw",
        "bus_dcvs",
        "bus",
    )

    /**
     * 已开启的 ZRAM 压缩算法。
     *
     * 虚拟内存关闭时 `disksize` 为 0，此时返回 null。
     * 多个已启用的 zram 设备会按设备号拼接，例如 `lz4/zstd`。
     */
    suspend fun getEnabledZramCompAlgorithm(): String? {
        val swapped = Files.readText("/proc/swaps").getOrNull().orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val token = line.trim().split(Regex("\\s+")).firstOrNull() ?: return@mapNotNull null
                token.substringAfterLast('/').takeIf { it.matches(zramDeviceName) }
            }
            .distinct()
            .sortedBy { it.removePrefix("zram").toIntOrNull() ?: Int.MAX_VALUE }
            .toList()
        val devices = swapped.ifEmpty { listOf("zram0") }

        val algorithms = devices.mapNotNull { device ->
            readEnabledZramAlgorithm(device)
        }.distinct()
        return algorithms.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    suspend fun getMemoryFreq(): Long? {
        val candidates = Files.list(devfreqRoot).getOrNull().orEmpty()
            .filter { path ->
                val name = path.substringAfterLast('/').lowercase()
                memoryFreqKeywords.any(name::contains)
            }

        return candidates.firstNotNullOfOrNull { path ->
            readMemoryFreqFromPath(path)
        }
    }

    suspend fun getMemoryInfo(): DeviceMemoryInfoModel {
        val memInfoContent = Files.readText(memInfoPath).getOrNull().orEmpty()
        if (memInfoContent.isBlank()) {
            return DeviceMemoryInfoModel()
        }

        val sizedValues = mutableMapOf<String, Long>()
        val rawValues = mutableMapOf<String, Long>()
        memInfoContent.lineSequence().forEach { line ->
            parseMemInfoLine(line)?.let { entry ->
                if (entry.hasUnit) {
                    sizedValues[entry.key] = entry.value
                } else {
                    rawValues[entry.key] = entry.value
                }
            }
        }

        return DeviceMemoryInfoModel(
            memTotal = sizedValues.byteValue("MemTotal"),
            memFree = sizedValues.byteValue("MemFree"),
            memAvailable = sizedValues.byteValue("MemAvailable"),
            buffers = sizedValues.byteValue("Buffers"),
            cached = sizedValues.byteValue("Cached"),
            swapCached = sizedValues.byteValue("SwapCached"),
            active = sizedValues.byteValue("Active"),
            inactive = sizedValues.byteValue("Inactive"),
            activeAnon = sizedValues.byteValue("Active(anon)"),
            inactiveAnon = sizedValues.byteValue("Inactive(anon)"),
            activeFile = sizedValues.byteValue("Active(file)"),
            inactiveFile = sizedValues.byteValue("Inactive(file)"),
            unevictable = sizedValues.byteValue("Unevictable"),
            mlocked = sizedValues.byteValue("Mlocked"),
            swapTotal = sizedValues.byteValue("SwapTotal"),
            swapFree = sizedValues.byteValue("SwapFree"),
            zswap = sizedValues.byteValue("Zswap"),
            zswapped = sizedValues.byteValue("Zswapped"),
            dirty = sizedValues.byteValue("Dirty"),
            writeback = sizedValues.byteValue("Writeback"),
            anonPages = sizedValues.byteValue("AnonPages"),
            mapped = sizedValues.byteValue("Mapped"),
            shmem = sizedValues.byteValue("Shmem"),
            kReclaimable = sizedValues.byteValue("KReclaimable"),
            slab = sizedValues.byteValue("Slab"),
            sReclaimable = sizedValues.byteValue("SReclaimable"),
            sUnreclaim = sizedValues.byteValue("SUnreclaim"),
            kernelStack = sizedValues.byteValue("KernelStack"),
            shadowCallStack = sizedValues.byteValue("ShadowCallStack"),
            pageTables = sizedValues.byteValue("PageTables"),
            secPageTables = sizedValues.byteValue("SecPageTables"),
            nfsUnstable = sizedValues.byteValue("NFS_Unstable"),
            bounce = sizedValues.byteValue("Bounce"),
            writebackTmp = sizedValues.byteValue("WritebackTmp"),
            commitLimit = sizedValues.byteValue("CommitLimit"),
            committedAs = sizedValues.byteValue("Committed_AS"),
            vmallocTotal = sizedValues.byteValue("VmallocTotal"),
            vmallocUsed = sizedValues.byteValue("VmallocUsed"),
            vmallocChunk = sizedValues.byteValue("VmallocChunk"),
            perCpu = sizedValues.byteValue("Percpu"),
            hardwareCorrupted = sizedValues.byteValue("HardwareCorrupted"),
            anonHugePages = sizedValues.byteValue("AnonHugePages"),
            shmemHugePages = sizedValues.byteValue("ShmemHugePages"),
            shmemPmdMapped = sizedValues.byteValue("ShmemPmdMapped"),
            fileHugePages = sizedValues.byteValue("FileHugePages"),
            filePmdMapped = sizedValues.byteValue("FilePmdMapped"),
            cmaTotal = sizedValues.byteValue("CmaTotal"),
            cmaFree = sizedValues.byteValue("CmaFree"),
            hugePageSize = sizedValues.byteValue("Hugepagesize"),
            hugeTlb = sizedValues.byteValue("Hugetlb"),
            directMap4k = sizedValues.byteValue("DirectMap4k"),
            directMap2M = sizedValues.byteValue("DirectMap2M"),
            directMap4M = sizedValues.byteValue("DirectMap4M"),
            directMap1G = sizedValues.byteValue("DirectMap1G"),
            lazyFree = sizedValues.byteValue("LazyFree"),
            hugePagesTotalCount = rawValues.rawValue("HugePages_Total"),
            hugePagesFreeCount = rawValues.rawValue("HugePages_Free"),
            hugePagesRsvdCount = rawValues.rawValue("HugePages_Rsvd"),
            hugePagesSurpCount = rawValues.rawValue("HugePages_Surp"),
        )
    }

    private fun parseMemInfoLine(line: String): ParsedMemInfoEntry? {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) {
            return null
        }

        val separatorIndex = trimmedLine.indexOf(':')
        if (separatorIndex <= 0) {
            return null
        }

        val key = trimmedLine.substring(0, separatorIndex).trim()
        val rawValuePart = trimmedLine.substring(separatorIndex + 1).trim()
        if (key.isEmpty() || rawValuePart.isEmpty()) {
            return null
        }

        val columns = rawValuePart.split(Regex("\\s+"))
        val number = columns.firstOrNull()?.toLongOrNull() ?: return null
        val unit = columns.getOrNull(1)
        return ParsedMemInfoEntry(
            key = key,
            value = convertToBytes(number, unit),
            hasUnit = !unit.isNullOrBlank(),
        )
    }

    private fun convertToBytes(value: Long, unit: String?): Long {
        return when (unit?.lowercase()) {
            null, "" -> value
            "b" -> value
            "kb", "kib" -> value * 1024L
            "mb", "mib" -> value * 1024L * 1024L
            "gb", "gib" -> value * 1024L * 1024L * 1024L
            else -> value
        }
    }

    private fun Map<String, Long>.byteValue(key: String): Long {
        return get(key) ?: 0L
    }

    private fun Map<String, Long>.rawValue(key: String): Long {
        return get(key) ?: 0L
    }

    private suspend fun readEnabledZramAlgorithm(device: String): String? {
        val diskSize = Files.readText("/sys/block/$device/disksize").getOrNull()
            ?.trim()
            ?.toLongOrNull()
            ?: 0L
        if (diskSize <= 0L) {
            return null
        }

        val raw = Files.readText("/sys/block/$device/comp_algorithm").getOrNull()?.trim()
            .orEmpty()
            .ifBlank {
                Files.readText("/sys/block/$device/algorithm").getOrNull()?.trim().orEmpty()
            }
        return parseZramCompAlgorithm(raw)
    }

    private fun parseZramCompAlgorithm(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        activeCompAlgorithm.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return trimmed.takeIf { !it.contains(' ') && !it.contains('[') }
    }

    private suspend fun readMemoryFreqFromPath(path: String): Long? {
        val rawValue = Files.readText("$path/cur_freq").getOrNull()?.trim().orEmpty()
        val frequency = rawValue
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toLongOrNull()
            ?: return null
        return normalizeFrequencyToMhz(frequency)
    }

    private fun normalizeFrequencyToMhz(value: Long): Long {
        return when {
            value >= 1_000_000L -> value / 1_000_000L
            value >= 1_000L -> value / 1_000L
            else -> value
        }
    }

    private data class ParsedMemInfoEntry(
        val key: String,
        val value: Long,
        val hasUnit: Boolean,
    )
}

private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

/**
 * 格式化内存大小为可读的字符串。
 */
infix fun Long.formatMemorySize(join: String = ""): String {
    if (this <= 0) return "0 B"

    var value = toDouble()
    var unitIndex = 0

    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }

    val text = when {
        value >= 100 -> "%.0f".format(value)
        value >= 10 -> "%.1f".format(value).trimEnd('0').trimEnd('.')
        else -> "%.2f".format(value).trimEnd('0').trimEnd('.')
    }

    return "$text$join${units[unitIndex]}"
}
