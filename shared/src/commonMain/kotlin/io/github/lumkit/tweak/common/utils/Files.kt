package io.github.lumkit.tweak.common.utils

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.asNativeFileBackend
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * 统一的 Native 文件访问入口。
 *
 * 这层只负责根据当前运行模式选择可用的底层实现，并把文件操作转发给对应的
 * [NativeFileService]。业务层不需要关心当前究竟是 Root、Shizuku 还是后续新增的
 * 其他 backend，只需要通过这里发起文件访问即可。
 *
 * backend 的选择规则来自 [GlobalViewModel.runtimeModeState]：
 * - `Root` 模式下会路由到 [NativeFileBackend.ROOT]
 * - 未知模式下会回落到 [NativeFileBackend.User]
 *
 * 所有方法都会返回 [NativeFileResult]：
 * - [NativeFileResult.Success] 表示操作成功，并携带具体结果
 * - [NativeFileResult.Failure] 表示操作失败，错误信息在 [NativeFileError] 中
 */
object Files {

    private suspend fun resolveBackend(): NativeFileBackend {
        val mode = GlobalViewModel.runtimeModeState.filterNotNull().first()
        return mode.asNativeFileBackend()
    }

    private suspend fun getService(): NativeFileService {
        val backend = resolveBackend()
        return NativeFiles.repository.getOrNull(backend)
            ?: throw RuntimeException("NativeFileService not found for backend: $backend")
    }

    /**
     * 判断目标路径是否存在。
     *
     * @param path 要检查的绝对路径
     * @return 成功时返回该路径是否存在，失败时返回对应错误信息
     */
    suspend fun exists(path: String): NativeFileResult<Boolean> {
        return getService().exists(path)
    }

    /**
     * 列出目录下的直接子项。
     *
     * 当前底层实现返回的是子项的完整路径列表，而不是仅文件名。
     *
     * @param path 目标目录路径
     * @return 成功时返回目录下的子项路径列表
     */
    suspend fun list(path: String): NativeFileResult<List<String>> {
        return getService().list(path)
    }

    /**
     * 列出目录下的直接子项，并附带是否为目录的元信息。
     *
     * @param path 目标目录路径
     * @return 成功时返回 [FileEntry] 列表
     */
    suspend fun listEntries(path: String): NativeFileResult<List<FileEntry>> {
        return getService().listEntries(path)
    }

    suspend fun zipEntries(path: String): NativeFileResult<List<ZipEntry>> {
        return getService().zipEntries(path)
    }

    /**
     * 以二进制形式读取文件内容。
     *
     * @param path 目标文件路径
     * @return 成功时返回完整文件字节数组
     */
    suspend fun readBytes(path: String): NativeFileResult<ByteArray> {
        return getService().readBytes(path)
    }

    /**
     * 以 UTF-8 文本形式读取文件内容。
     *
     * @param path 目标文件路径
     * @return 成功时返回 UTF-8 解码后的文本内容
     */
    suspend fun readText(path: String): NativeFileResult<String> {
        return getService().readText(path)
    }

    /**
     * 以二进制形式写入文件。
     *
     * 如果目标文件不存在，底层实现会尝试先创建父目录并创建文件；如果已存在，
     * 则会覆盖原内容。
     *
     * @param path 目标文件路径
     * @param bytes 要写入的完整字节数组
     * @return 成功时返回 [Unit]
     */
    suspend fun writeBytes(path: String, bytes: ByteArray): NativeFileResult<Unit> {
        return getService().writeBytes(path, bytes)
    }

    /**
     * 以 UTF-8 文本形式写入文件。
     *
     * @param path 目标文件路径
     * @param text 要写入的文本内容
     * @return 成功时返回 [Unit]
     */
    suspend fun writeText(path: String, text: String): NativeFileResult<Unit> {
        return getService().writeText(path, text)
    }

    /**
     * 删除文件或目录。
     *
     * 当目标是目录时，只有 [recursive] 为 `true` 才会递归删除其内容；否则会交由
     * 底层实现按非递归方式删除，目录非空时通常会失败。
     *
     * @param path 目标路径
     * @param recursive 是否递归删除目录内容
     * @return 成功时返回 [Unit]
     */
    suspend fun delete(path: String, recursive: Boolean = false): NativeFileResult<Unit> {
        return getService().delete(path, recursive)
    }

    /**
     * 递归创建目录。
     *
     * 行为类似 `mkdir -p`，会尽可能补齐缺失的父目录。
     *
     * @param path 要创建的目录路径
     * @return 成功时返回 [Unit]
     */
    suspend fun mkdirs(path: String): NativeFileResult<Unit> {
        return getService().mkdirs(path)
    }

    /**
     * 复制文件或目录到目标路径。
     *
     * 当前底层实现支持递归复制目录，并在必要时创建目标父目录。
     *
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     * @param overwrite 目标已存在时是否允许覆盖
     * @return 成功时返回 [Unit]
     */
    suspend fun copy(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean = false,
    ): NativeFileResult<Unit> {
        return getService().copy(sourcePath, targetPath, overwrite)
    }

    /**
     * 移动文件或目录到目标路径。
     *
     * 当前底层实现优先尝试原子 `rename`，跨分区场景下会退化为“复制后删除源文件”。
     *
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     * @param overwrite 目标已存在时是否允许覆盖
     * @return 成功时返回 [Unit]
     */
    suspend fun move(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean = false,
    ): NativeFileResult<Unit> {
        return getService().move(sourcePath, targetPath, overwrite)
    }

    /**
     * 修改文件或目录权限。
     *
     * [mode] 需要传八进制权限字符串，例如 `0644`、`0755`。
     *
     * @param path 目标路径
     * @param mode 八进制权限字符串
     * @return 成功时返回 [Unit]
     */
    suspend fun chmod(path: String, mode: String): NativeFileResult<Unit> {
        return getService().chmod(path, mode)
    }

    /**
     * 获取文件或目录占用大小（字节）。
     *
     * - 普通文件：返回文件大小
     * - 目录：递归累加所有子项大小（不跟随符号链接）
     *
     * @param path 目标路径
     * @return 成功时返回占用字节数
     */
    suspend fun length(path: String): NativeFileResult<Long> {
        return getService().length(path)
    }

    suspend fun readCpuCycles(coreIndex: Int): NativeFileResult<Long> {
        return getService().readCpuCycles(coreIndex)
    }

    /**
     * 从 content:// URI 解压 ZIP 文件到指定目录。
     * 通过 ContentResolver 打开输入流，在特权进程中完成解压。
     *
     * @param uriString content:// 或 file:// URI 字符串
     * @param targetDir 目标目录绝对路径，不存在时自动创建
     * @return 成功时返回 [Unit]
     */
    suspend fun unzipFromUri(uriString: String, targetDir: String): NativeFileResult<Unit> {
        return getService().unzipFromUri(uriString, targetDir)
    }

    /**
     * 从绝对路径解压 ZIP 到指定目录。
     */
    suspend fun unzipFromPath(sourcePath: String, targetDir: String): NativeFileResult<Unit> {
        return getService().unzipFromPath(sourcePath, targetDir)
    }

}


expect infix fun String.joinPath(childPath: String): String

expect fun Uri.documentFile(): DocumentFile?
