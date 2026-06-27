package io.github.lumkit.tweak.common.utils

enum class NativeFileBackend {
    User,
    ROOT,
    SHIZUKU,
}

data class NativeFileError(
    val backend: NativeFileBackend,
    val operation: String,
    val primaryPath: String? = null,
    val secondaryPath: String? = null,
    val message: String,
)

/**
 * 压缩包中的单个条目元信息。
 *
 * 该模型由底层特权文件服务读取 ZIP central directory 后构造，
 * 用于在 shared 层统一承载压缩包 entry 的基础属性。
 *
 * @property name entry 在压缩包中的完整路径
 * @property isDirectory 当前 entry 是否为目录
 * @property size 解压后的原始大小，未知时可能为 `-1`
 * @property compressedSize 压缩后的大小，未知时可能为 `-1`
 * @property crc entry 的 CRC32 校验值，未知时可能为 `-1`
 * @property time entry 的最后修改时间戳，未知时可能为 `-1`
 * @property offset entry 对应 local file header 在 zip 文件中的字节偏移
 */
data class ZipEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val crc: Long,
    val time: Long,
    val offset: Long,
)

sealed interface NativeFileResult<out T> {
    data class Success<T>(val value: T) : NativeFileResult<T>

    data class Failure(val error: NativeFileError) : NativeFileResult<Nothing>
}

interface NativeFileService {
    val backend: NativeFileBackend

    suspend fun exists(path: String): NativeFileResult<Boolean>

    suspend fun list(path: String): NativeFileResult<List<String>>

    suspend fun zipEntries(path: String): NativeFileResult<List<ZipEntry>>

    suspend fun readBytes(path: String): NativeFileResult<ByteArray>

    suspend fun readText(path: String): NativeFileResult<String>

    suspend fun writeBytes(path: String, bytes: ByteArray): NativeFileResult<Unit>

    suspend fun writeText(path: String, text: String): NativeFileResult<Unit>

    suspend fun delete(path: String, recursive: Boolean = false): NativeFileResult<Unit>

    suspend fun mkdirs(path: String): NativeFileResult<Unit>

    suspend fun copy(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean = false,
    ): NativeFileResult<Unit>

    suspend fun move(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean = false,
    ): NativeFileResult<Unit>

    suspend fun chmod(path: String, mode: String): NativeFileResult<Unit>
}

interface NativeFileServiceProvider {
    fun getOrNull(backend: NativeFileBackend): NativeFileService?
}

expect object PlatformNativeFileServices : NativeFileServiceProvider

class NativeFileRepository(
    private val provider: NativeFileServiceProvider = PlatformNativeFileServices,
) {
    fun getOrNull(backend: NativeFileBackend): NativeFileService? {
        return provider.getOrNull(backend)
    }

    fun require(backend: NativeFileBackend): NativeFileService {
        return getOrNull(backend)
            ?: error("Native file backend $backend is not available on this platform")
    }
}

object NativeFiles {
    val repository: NativeFileRepository = NativeFileRepository()
}

fun <T> NativeFileResult<T>.getOrNull(): T? {
    return (this as? NativeFileResult.Success<T>)?.value
}
