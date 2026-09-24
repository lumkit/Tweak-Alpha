package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileBackend
import io.github.lumkit.tweak.common.utils.NativeFileError
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.PrivilegedWorkDir
import io.github.lumkit.tweak.common.utils.StorageUtils
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.joinPath
import io.github.lumkit.tweak.common.utils.openPrivilegedReadOnlyFd
import io.github.lumkit.tweak.model.RuntimeModeStore
import io.github.lumkit.tweak.model.asNativeFileBackend
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.UUID

/**
 * 线刷 ROM 压缩包解压与工作区管理。
 */
object LineFlashArchiveHelper {

    private val ARCHIVE_SUFFIXES = listOf(
        ".zip",
        ".tar.gz",
        ".tgz",
        ".tar",
        ".gz",
    )

    fun isArchivePath(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return ARCHIVE_SUFFIXES.any { lower.endsWith(it) }
    }

    suspend fun ensureWorkRoot(): NativeFileResult<Unit> {
        return try {
            PrivilegedWorkDir.ensureWritable(
                path = ConstCommon.Path.TWEAK_ALPHA_ROOT,
                workRoot = ConstCommon.Path.TWEAK_ALPHA_ROOT,
                mode = "0777",
            )
            PrivilegedWorkDir.ensureWritable(
                path = ConstCommon.Path.LINE_FLASH_DIR,
                workRoot = ConstCommon.Path.TWEAK_ALPHA_ROOT,
                mode = "0777",
            )
            NativeFileResult.Success(Unit)
        } catch (e: Exception) {
            NativeFileResult.Failure(
                NativeFileError(
                    backend = NativeFileBackend.User,
                    operation = "ensureWorkRoot",
                    primaryPath = ConstCommon.Path.TWEAK_ALPHA_ROOT,
                    message = e.message ?: "ensureWorkRoot failed",
                ),
            )
        }
    }

    suspend fun createSessionDir(): String {
        ensureWorkRoot()
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val dir = ConstCommon.Path.lineFlashSession(sessionId)
        when (val result = Files.mkdirs(dir)) {
            is NativeFileResult.Success -> Unit
            is NativeFileResult.Failure -> error(result.error.message)
        }
        return sessionId
    }

    suspend fun clearSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        Files.delete(ConstCommon.Path.lineFlashSession(sessionId), recursive = true)
    }

    suspend fun clearAllSessionsExcept(keepSessionId: String?) {
        val children = Files.list(ConstCommon.Path.LINE_FLASH_DIR).getOrNull().orEmpty()
        children.forEach { path ->
            val name = path.substringAfterLast('/')
            if (keepSessionId == null || name != keepSessionId) {
                Files.delete(path, recursive = true)
            }
        }
    }

    /**
     * 解压前校验：可用空间需 ≥ 压缩包体积 × 2。
     * @return Pair(archiveBytes, freeBytes)；不足时抛出 [InsufficientSpaceException]
     */
    suspend fun requireEnoughSpace(archivePath: String): Pair<Long, Long> {
        val archiveBytes = getFileSize(archivePath)
        require(archiveBytes > 0) { "无法获取压缩包大小: $archivePath" }
        val required = archiveBytes * 2
        val free = StorageUtils.getFreeBytes("/data")
        if (free < 0) {
            error("无法获取可用存储空间")
        }
        if (free < required) {
            throw InsufficientSpaceException(archiveBytes = archiveBytes, freeBytes = free, requiredBytes = required)
        }
        return archiveBytes to free
    }

    suspend fun extractToSession(
        archivePath: String,
        sessionId: String,
        onProgressMessage: (String) -> Unit,
    ): String {
        requireEnoughSpace(archivePath)
        val targetDir = ConstCommon.Path.lineFlashSession(sessionId)
        onProgressMessage("正在解压...")
        when (val type = detectArchiveType(archivePath)) {
            ArchiveType.Zip -> {
                when (val result = Files.unzipFromPath(archivePath, targetDir)) {
                    is NativeFileResult.Success -> Unit
                    is NativeFileResult.Failure -> error(result.error.message)
                }
            }

            ArchiveType.Tar, ArchiveType.TarGz, ArchiveType.Gz -> {
                extractWithTar(archivePath, targetDir, type)
            }
        }
        onProgressMessage("解压完成，定位 ROM 目录...")
        return resolveRomRoot(targetDir)
    }

    private suspend fun extractWithTar(archivePath: String, targetDir: String, type: ArchiveType) {
        val quotedArchive = shellQuote(archivePath)
        val quotedTarget = shellQuote(targetDir)
        val command = when (type) {
            ArchiveType.Tar -> "tar -xf $quotedArchive -C $quotedTarget"
            ArchiveType.TarGz, ArchiveType.Gz -> {
                // .gz 单文件也用 tar -xzf 尝试；失败再提示
                "tar -xzf $quotedArchive -C $quotedTarget"
            }
            ArchiveType.Zip -> error("zip should not use tar")
        }
        val output = ReusableShells.execSync(command)
        val failed = listOf("error", "not found", "No such", "cannot", "invalid")
            .any { output.contains(it, ignoreCase = true) }
        if (failed && output.isNotBlank()) {
            // busybox 回退
            val busyboxCmd = when (type) {
                ArchiveType.Tar -> "busybox tar -xf $quotedArchive -C $quotedTarget"
                ArchiveType.TarGz, ArchiveType.Gz -> "busybox tar -xzf $quotedArchive -C $quotedTarget"
                ArchiveType.Zip -> error("zip should not use tar")
            }
            val busyboxOut = ReusableShells.execSync(busyboxCmd)
            val busyboxFailed = listOf("error", "not found", "No such", "cannot", "invalid")
                .any { busyboxOut.contains(it, ignoreCase = true) }
            if (busyboxFailed) {
                error(busyboxOut.ifBlank { output.ifBlank { "解压失败" } })
            }
        }
    }

    suspend fun resolveRomRoot(extractedDir: String): String {
        if (looksLikeRomRoot(extractedDir)) return extractedDir
        val children = Files.listEntries(extractedDir).getOrNull().orEmpty()
            .filter { it.isDirectory }
        children.firstOrNull { looksLikeRomRoot(it.path) }?.path?.let { return it }
        // 再往下探一层
        children.forEach { child ->
            val nested = Files.listEntries(child.path).getOrNull().orEmpty()
                .filter { it.isDirectory }
            nested.firstOrNull { looksLikeRomRoot(it.path) }?.path?.let { return it }
        }
        error("解压后未找到合法线刷包目录（需要包含 images/ 与刷写脚本）")
    }

    private suspend fun looksLikeRomRoot(path: String): Boolean {
        val images = path joinPath "images"
        if (Files.exists(images).getOrNull() != true) return false
        val entries = Files.list(path).getOrNull().orEmpty()
        return entries.any {
            it.endsWith(".bat", true) || it.endsWith(".sh", true)
        }
    }

    private fun detectArchiveType(path: String): ArchiveType {
        val lower = path.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveType.TarGz
            lower.endsWith(".tar") -> ArchiveType.Tar
            lower.endsWith(".gz") -> ArchiveType.Gz
            lower.endsWith(".zip") -> ArchiveType.Zip
            else -> error("不支持的压缩格式: ${path.substringAfterLast('.')}")
        }
    }

    private suspend fun getFileSize(path: String): Long {
        val backend = resolveBackend()
        return openPrivilegedReadOnlyFd(backend, path).use { it.statSize }
    }

    private suspend fun resolveBackend(): NativeFileBackend {
        val mode = RuntimeModeStore.mode.filterNotNull().first()
        return mode.asNativeFileBackend().also {
            require(it != NativeFileBackend.User) { "当前运行模式不支持特权文件操作" }
        }
    }

    private fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    private enum class ArchiveType {
        Zip, Tar, TarGz, Gz
    }

    class InsufficientSpaceException(
        val archiveBytes: Long,
        val freeBytes: Long,
        val requiredBytes: Long,
    ) : IllegalStateException(
        "insufficient space: need $requiredBytes, free $freeBytes, archive $archiveBytes"
    )
}
