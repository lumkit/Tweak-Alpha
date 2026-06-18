package io.github.lumkit.tweak.common.utils

/**
 * 存储信息工具类。
 *
 * 提供设备内部存储空间、用户分身信息和闪存协议类型的获取能力。
 * 底层通过 C++ JNI（statvfs / readdir / sysfs）实现，性能开销极小。
 */
object StorageUtils {

    /**
     * 获取内部存储总空间大小，单位字节。
     * 默认查询 /data 分区。
     */
    suspend fun getTotalBytes(path: String = "/data"): Long {
        return PlatformStorageSource.getTotalBytes(path)
    }

    /**
     * 获取内部存储已使用空间大小，单位字节。
     * 默认查询 /data 分区。
     */
    suspend fun getUsedBytes(path: String = "/data"): Long {
        return PlatformStorageSource.getUsedBytes(path)
    }

    /**
     * 获取可用空间大小，单位字节。
     */
    suspend fun getFreeBytes(path: String = "/data"): Long {
        val total = getTotalBytes(path)
        val used = getUsedBytes(path)
        if (total < 0 || used < 0) return -1
        return total - used
    }

    /**
     * 获取用户空间列表。
     * 返回用户 ID 字符串列表，如 ["0", "10", "999"]。
     * "0" 为主用户，其他为分身/工作空间用户。
     */
    suspend fun getUserProfiles(): List<String> {
        return Files.list("/storage/emulated")
            .getOrNull()
            .orEmpty()
            .map {
                it.substringAfterLast('/')
            }
    }

    /**
     * 获取闪存协议类型及版本。
     * 返回如 "UFS 4.0"、"eMMC 5.1"、"NVMe" 或 "Unknown"。
     * 需要 ROOT 权限读取 sysfs 节点。
     */
    suspend fun getFlashType(): String {
        return detectFlashType()
    }
}

private suspend fun detectFlashType(): String {
    // 1. UFS 检测
    val scsiHosts = Files.list("/sys/class/scsi_host").getOrNull().orEmpty()
    if (scsiHosts.any { it.substringAfterLast('/').startsWith("host") }) {
        val version = detectUfsVersion()
        return if (version != null) "UFS $version" else "UFS"
    }

    // 2. NVMe 检测
    val nvmeDevices = Files.list("/sys/class/nvme").getOrNull().orEmpty()
    if (nvmeDevices.any { it.substringAfterLast('/').startsWith("nvme") }) {
        return "NVMe"
    }

    // 3. eMMC 检测
    val mmcHosts = Files.list("/sys/class/mmc_host").getOrNull().orEmpty()
    if (mmcHosts.any { it.substringAfterLast('/').startsWith("mmc") }) {
        val version = detectEmmcVersion()
        return if (version != null) "eMMC $version" else "eMMC"
    }

    return "Unknown"
}

private suspend fun detectUfsVersion(): String? {
    // 策略1：扫描 /sys/bus/platform/devices/ 下带 ufshc/ufs 的设备
    val platformDevices = Files.list("/sys/bus/platform/devices").getOrNull().orEmpty()
    for (device in platformDevices) {
        val name = device.substringAfterLast('/')
        if ("ufshc" in name || (name.endsWith(".ufs") && "ufs" in name)) {
            val sv = readText("$device/spec_version")
            if (sv != null) return parseUfsSpecVersion(sv)
        }
    }

    // 策略2：通过 /sys/class/scsi_host/host*/device/ 查找
    val hosts = Files.list("/sys/class/scsi_host").getOrNull().orEmpty()
    for (host in hosts) {
        val hostName = host.substringAfterLast('/')
        if (!hostName.startsWith("host")) continue
        // 尝试 device 和上级目录
        val basePaths = listOf(
            "/sys/class/scsi_host/$hostName/device/spec_version",
            "/sys/class/scsi_host/$hostName/device/../spec_version",
        )
        for (path in basePaths) {
            val sv = readText(path)
            if (sv != null) return parseUfsSpecVersion(sv)
        }
    }

    // 策略3：通过 /sys/block/sda 反向追踪 UFS controller
    val sdaDevicePath = readText("/sys/block/sda/device/../../spec_version")
    if (sdaDevicePath != null) return parseUfsSpecVersion(sdaDevicePath)

    // 策略4：扫描 /sys/devices/platform/ 递归查找所有可能的 spec_version
    val platformDir = Files.list("/sys/devices/platform").getOrNull().orEmpty()
    for (entry in platformDir) {
        val entryName = entry.substringAfterLast('/')
        // 匹配 soc 目录或直接包含 ufs 的设备
        if (entryName == "soc" || "ufs" in entryName.lowercase()) {
            val sv = findSpecVersionIn(entry)
            if (sv != null) return parseUfsSpecVersion(sv)
        }
    }

    return null
}

private suspend fun findSpecVersionIn(basePath: String): String? {
    // 先尝试直接读
    val direct = readText("$basePath/spec_version")
    if (direct != null) return direct

    // 扫描子目录中包含 ufs 的
    val children = Files.list(basePath).getOrNull().orEmpty()
    for (child in children) {
        val childName = child.substringAfterLast('/')
        if ("ufshc" in childName || "ufs" in childName.lowercase()) {
            val sv = readText("$child/spec_version")
            if (sv != null) return sv
        }
    }
    return null
}

private fun parseUfsSpecVersion(raw: String): String? {
    // 如果已经是 x.y 格式直接返回
    if (raw.contains('.')) return raw

    // 十六进制编码: 0x0310 = 3.1, 0x0400 = 4.0, 0x0401 = 4.1
    val hexVal = raw.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: return null
    val major = ((hexVal shr 8) and 0xFF).toInt()
    val minor = (hexVal and 0xFF).toInt()
    if (major in 1..9) return "$major.$minor"
    return null
}

private suspend fun detectEmmcVersion(): String? {
    val extCsdRev = readText("/sys/block/mmcblk0/device/ext_csd_rev") ?: return null
    val rev = extCsdRev.toIntOrNull() ?: return null
    return when (rev) {
        9 -> "5.1.1"
        8 -> "5.1"
        7 -> "5.0"
        6 -> "4.5"
        5 -> "4.41"
        4 -> "4.4"
        3 -> "4.3"
        2 -> "4.2"
        1 -> "4.1"
        0 -> "4.0"
        else -> null
    }
}

private suspend fun readText(path: String): String? {
    return when (val result = Files.readText(path)) {
        is NativeFileResult.Success -> result.value.trim().ifEmpty { null }
        is NativeFileResult.Failure -> null
    }
}

internal expect object PlatformStorageSource {
    suspend fun getTotalBytes(path: String): Long
    suspend fun getUsedBytes(path: String): Long
    suspend fun getUserProfiles(): List<String>
}
