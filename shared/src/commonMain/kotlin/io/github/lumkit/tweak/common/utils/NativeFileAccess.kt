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

sealed interface NativeFileResult<out T> {
    data class Success<T>(val value: T) : NativeFileResult<T>

    data class Failure(val error: NativeFileError) : NativeFileResult<Nothing>
}

interface NativeFileService {
    val backend: NativeFileBackend

    suspend fun exists(path: String): NativeFileResult<Boolean>

    suspend fun list(path: String): NativeFileResult<List<String>>

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
