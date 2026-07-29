package io.github.lumkit.tweak.common.utils

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.sharednative.IRootFileService
import io.github.lumkit.tweak.sharednative.NativeFileBundles
import io.github.lumkit.tweak.sharednative.RootFileService
import io.github.lumkit.tweak.sharednative.ShizukuFileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual object PlatformNativeFileServices : NativeFileServiceProvider {
    private val rootService = RootNativeFileService
    private val shizukuService = ShizukuNativeFileService

    override fun getOrNull(backend: NativeFileBackend): NativeFileService? {
        return when (backend) {
            NativeFileBackend.ROOT -> rootService
            NativeFileBackend.SHIZUKU -> shizukuService
            NativeFileBackend.User -> null
        }
    }
}

internal object RootFileServiceConnectionManager {
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var service: IRootFileService? = null

    @Volatile
    private var connection: ServiceConnection? = null

    suspend fun getService(): IRootFileService {
        service?.let { return it }
        return mutex.withLock {
            service?.let { return it }
            bindLocked()
        }
    }

    /** 解绑 Root UserService，释放我们拉起的特权子进程。 */
    suspend fun release() = mutex.withLock {
        val bound = connection
        service = null
        connection = null
        if (bound == null) return@withLock
        withContext(Dispatchers.Main.immediate) {
            runCatching { RootService.unbind(bound) }
        }
    }

    private suspend fun bindLocked(): IRootFileService {
        val shell = withContext(Dispatchers.IO) { Shell.getShell() }
        if (!shell.isRoot) {
            throw IllegalStateException("Root shell is unavailable")
        }

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val boundConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val remoteService = IRootFileService.Stub.asInterface(binder)
                        if (remoteService == null) {
                            continuation.resumeWithException(
                                IllegalStateException("Root file service binder is null")
                            )
                            return
                        }
                        service = remoteService
                        connection = this
                        continuation.resume(remoteService)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                        connection = null
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        try {
                            RootService.unbind(boundConnection)
                        } catch (_: Throwable) {
                        }
                        if (connection === boundConnection) {
                            connection = null
                            service = null
                        }
                    }
                }

                try {
                    RootService.bind(
                        Intent(application, RootFileService::class.java),
                        boundConnection
                    )
                } catch (throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            }
        }
    }
}

internal suspend fun openPrivilegedReadOnlyFd(
    backend: NativeFileBackend,
    path: String,
): ParcelFileDescriptor {
    return when (backend) {
        NativeFileBackend.ROOT -> RootFileServiceConnectionManager.getService().openReadOnlyFd(path)
        NativeFileBackend.SHIZUKU -> ShizukuFileServiceConnectionManager.getService().openReadOnlyFd(path)
        NativeFileBackend.User -> throw IllegalArgumentException("User backend does not support privileged fd: $path")
    }
}

internal suspend fun openPrivilegedWriteOnlyFd(
    backend: NativeFileBackend,
    path: String,
    create: Boolean = true,
    truncate: Boolean = true,
): ParcelFileDescriptor {
    return when (backend) {
        NativeFileBackend.ROOT -> RootFileServiceConnectionManager.getService()
            .openWriteOnlyFd(path, create, truncate)

        NativeFileBackend.SHIZUKU -> ShizukuFileServiceConnectionManager.getService()
            .openWriteOnlyFd(path, create, truncate)

        NativeFileBackend.User -> throw IllegalArgumentException("User backend does not support privileged fd: $path")
    }
}

private object RootNativeFileService : NativeFileService {
    override val backend: NativeFileBackend = NativeFileBackend.ROOT

    override suspend fun exists(path: String): NativeFileResult<Boolean> {
        return execute(
            operation = "exists",
            primaryPath = path,
            transform = { bundle -> bundle.getBoolean(NativeFileBundles.KEY_BOOLEAN) },
            block = { service -> service.exists(path) },
        )
    }

    override suspend fun list(path: String): NativeFileResult<List<String>> {
        return execute(
            operation = "list",
            primaryPath = path,
            transform = { bundle ->
                bundle.getStringArrayList(NativeFileBundles.KEY_STRING_LIST)?.toList().orEmpty()
            },
            block = { service -> service.list(path) },
        )
    }

    override suspend fun listEntries(path: String): NativeFileResult<List<FileEntry>> {
        return execute(
            operation = "listEntries",
            primaryPath = path,
            transform = { bundle -> bundle.toFileEntries() },
            block = { service -> service.listEntries(path) },
        )
    }

    override suspend fun zipEntries(path: String): NativeFileResult<List<ZipEntry>> {
        return execute(
            operation = "zipEntries",
            primaryPath = path,
            transform = { bundle -> bundle.toZipEntries() },
            block = { service -> service.zipEntries(path) },
        )
    }

    override suspend fun readBytes(path: String): NativeFileResult<ByteArray> {
        return execute(
            operation = "readBytes",
            primaryPath = path,
            transform = { bundle -> bundle.getByteArray(NativeFileBundles.KEY_BYTES) ?: ByteArray(0) },
            block = { service -> service.readBytes(path) },
        )
    }

    override suspend fun readText(path: String): NativeFileResult<String> {
        return execute(
            operation = "readText",
            primaryPath = path,
            transform = { bundle -> bundle.getString(NativeFileBundles.KEY_STRING).orEmpty() },
            block = { service -> service.readText(path) },
        )
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): NativeFileResult<Unit> {
        return executeUnit("writeBytes", path) { service ->
            service.writeBytes(path, bytes)
        }
    }

    override suspend fun writeText(path: String, text: String): NativeFileResult<Unit> {
        return executeUnit("writeText", path) { service ->
            service.writeText(path, text)
        }
    }

    override suspend fun delete(path: String, recursive: Boolean): NativeFileResult<Unit> {
        return executeUnit("delete", path) { service ->
            service.delete(path, recursive)
        }
    }

    override suspend fun mkdirs(path: String): NativeFileResult<Unit> {
        return executeUnit("mkdirs", path) { service ->
            service.mkdirs(path)
        }
    }

    override suspend fun copy(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean,
    ): NativeFileResult<Unit> {
        return executeUnit("copy", sourcePath, targetPath) { service ->
            service.copy(sourcePath, targetPath, overwrite)
        }
    }

    override suspend fun move(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean,
    ): NativeFileResult<Unit> {
        return executeUnit("move", sourcePath, targetPath) { service ->
            service.move(sourcePath, targetPath, overwrite)
        }
    }

    override suspend fun chmod(path: String, mode: String): NativeFileResult<Unit> {
        return executeUnit("chmod", path) { service ->
            service.chmod(path, mode)
        }
    }

    override suspend fun length(path: String): NativeFileResult<Long> {
        return execute(
            operation = "length",
            primaryPath = path,
            transform = { bundle -> bundle.getLong(NativeFileBundles.KEY_LONG) },
            block = { service -> service.length(path) },
        )
    }

    override suspend fun readCpuCycles(coreIndex: Int): NativeFileResult<Long> {
        return execute(
            operation = "readCpuCycles",
            primaryPath = "cpu$coreIndex",
            transform = { bundle -> bundle.getLong(NativeFileBundles.KEY_LONG) },
            block = { service -> service.readCpuCycles(coreIndex) },
        )
    }

    override suspend fun unzipFromUri(uriString: String, targetDir: String): NativeFileResult<Unit> {
        return executeUnit("unzipFromUri", uriString, targetDir) { service ->
            val uri = uriString.toUri()
            val pfd = application.contentResolver.openFileDescriptor(uri, "r")
                ?: throw RuntimeException("Cannot open file descriptor from URI: $uriString")
            service.unzipToDir(pfd, targetDir)
        }
    }

    override suspend fun unzipFromPath(sourcePath: String, targetDir: String): NativeFileResult<Unit> {
        return executeUnit("unzipFromPath", sourcePath, targetDir) { service ->
            service.unzipPathToDir(sourcePath, targetDir)
        }
    }

    override suspend fun execDetached(command: String): NativeFileResult<Unit> {
        return executeUnit("execDetached", command) { service ->
            service.execDetached(command)
        }
    }

    override suspend fun startTweakServerEmbedded(packageName: String): NativeFileResult<Unit> {
        return executeUnit("startTweakServerEmbedded", packageName) { service ->
            service.startTweakServerEmbedded(packageName)
        }
    }

    override suspend fun stopTweakServerEmbedded(): NativeFileResult<Unit> {
        return executeUnit("stopTweakServerEmbedded", "") { service ->
            service.stopTweakServerEmbedded()
        }
    }

    suspend fun <T> execute(
        operation: String,
        primaryPath: String,
        secondaryPath: String? = null,
        transform: (Bundle) -> T,
        block: (IRootFileService) -> Bundle,
    ): NativeFileResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                val bundle = block(RootFileServiceConnectionManager.getService())
                bundle.classLoader = javaClass.classLoader
                if (!bundle.getBoolean(NativeFileBundles.KEY_SUCCESS)) {
                    NativeFileResult.Failure(
                        NativeFileError(
                            backend = backend,
                            operation = operation,
                            primaryPath = primaryPath,
                            secondaryPath = secondaryPath,
                            message = bundle.getString(NativeFileBundles.KEY_MESSAGE)
                                ?: "Unknown native file error",
                        )
                    )
                } else {
                    NativeFileResult.Success(transform(bundle))
                }
            } catch (throwable: Throwable) {
                NativeFileResult.Failure(
                    NativeFileError(
                        backend = backend,
                        operation = operation,
                        primaryPath = primaryPath,
                        secondaryPath = secondaryPath,
                        message = throwable.message ?: throwable.toString(),
                    )
                )
            }
        }
    }

    suspend fun executeUnit(
        operation: String,
        primaryPath: String,
        secondaryPath: String? = null,
        block: (IRootFileService) -> Bundle,
    ): NativeFileResult<Unit> {
        return execute(
            operation = operation,
            primaryPath = primaryPath,
            secondaryPath = secondaryPath,
            transform = { },
            block = block,
        )
    }
}

internal object ShizukuFileServiceConnectionManager {
    private val mutex = Mutex()

    @Volatile
    private var service: IRootFileService? = null

    @Volatile
    private var connection: ServiceConnection? = null

    @Volatile
    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    suspend fun getService(): IRootFileService {
        service?.let { return it }
        return mutex.withLock {
            service?.let { return it }
            bindLocked()
        }
    }

    /**
     * 解绑 Shizuku UserService。
     * [remove]=true 时销毁 `file_service` 子进程（重启 App / 切换运行模式时应销毁）。
     */
    suspend fun release(remove: Boolean = true) = mutex.withLock {
        val args = userServiceArgs ?: Shizuku.UserServiceArgs(
            ComponentName(application, ShizukuFileService::class.java),
        )
            .daemon(true)
            .processNameSuffix("file_service")
            .debuggable(isDebugBuild())
            .tag("shizuku_file_service")
        val bound = connection
        service = null
        connection = null
        userServiceArgs = null
        runCatching {
            Shizuku.unbindUserService(args, bound, remove)
        }
    }

    private suspend fun bindLocked(): IRootFileService {
        if (!Shizuku.pingBinder()) {
            throw IllegalStateException("Shizuku service is not running")
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("Shizuku permission not granted")
        }

        return suspendCancellableCoroutine { continuation ->
            val args = Shizuku.UserServiceArgs(
                ComponentName(application, ShizukuFileService::class.java)
            )
                .daemon(true)
                .processNameSuffix("file_service")
                .debuggable(isDebugBuild())
                .tag("shizuku_file_service")

            val boundConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val remoteService = IRootFileService.Stub.asInterface(binder)
                    if (remoteService == null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Shizuku file service binder is null")
                            )
                        }
                        return
                    }
                    service = remoteService
                    connection = this
                    userServiceArgs = args
                    if (continuation.isActive) {
                        continuation.resume(remoteService)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                    connection = null
                    userServiceArgs = null
                }
            }

            continuation.invokeOnCancellation {
                try {
                    Shizuku.unbindUserService(args, boundConnection, false)
                } catch (_: Throwable) {
                }
            }

            try {
                Shizuku.bindUserService(args, boundConnection)
            } catch (throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
        }
    }
}

/** 释放 Root / Shizuku 特权文件服务子进程。 */
internal suspend fun releasePrivilegedFileServices() {
    runCatching { RootFileServiceConnectionManager.release() }
    runCatching { ShizukuFileServiceConnectionManager.release(remove = true) }
}

private object ShizukuNativeFileService : NativeFileService {
    override val backend: NativeFileBackend = NativeFileBackend.SHIZUKU

    override suspend fun exists(path: String): NativeFileResult<Boolean> {
        return execute(
            operation = "exists",
            primaryPath = path,
            transform = { bundle -> bundle.getBoolean(NativeFileBundles.KEY_BOOLEAN) },
            block = { service -> service.exists(path) },
        )
    }

    override suspend fun list(path: String): NativeFileResult<List<String>> {
        return execute(
            operation = "list",
            primaryPath = path,
            transform = { bundle ->
                bundle.getStringArrayList(NativeFileBundles.KEY_STRING_LIST)?.toList().orEmpty()
            },
            block = { service -> service.list(path) },
        )
    }

    override suspend fun listEntries(path: String): NativeFileResult<List<FileEntry>> {
        return execute(
            operation = "listEntries",
            primaryPath = path,
            transform = { bundle -> bundle.toFileEntries() },
            block = { service -> service.listEntries(path) },
        )
    }

    override suspend fun zipEntries(path: String): NativeFileResult<List<ZipEntry>> {
        return execute(
            operation = "zipEntries",
            primaryPath = path,
            transform = { bundle -> bundle.toZipEntries() },
            block = { service -> service.zipEntries(path) },
        )
    }

    override suspend fun readBytes(path: String): NativeFileResult<ByteArray> {
        return execute(
            operation = "readBytes",
            primaryPath = path,
            transform = { bundle -> bundle.getByteArray(NativeFileBundles.KEY_BYTES) ?: ByteArray(0) },
            block = { service -> service.readBytes(path) },
        )
    }

    override suspend fun readText(path: String): NativeFileResult<String> {
        return execute(
            operation = "readText",
            primaryPath = path,
            transform = { bundle -> bundle.getString(NativeFileBundles.KEY_STRING).orEmpty() },
            block = { service -> service.readText(path) },
        )
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): NativeFileResult<Unit> {
        return executeUnit("writeBytes", path) { service ->
            service.writeBytes(path, bytes)
        }
    }

    override suspend fun writeText(path: String, text: String): NativeFileResult<Unit> {
        return executeUnit("writeText", path) { service ->
            service.writeText(path, text)
        }
    }

    override suspend fun delete(path: String, recursive: Boolean): NativeFileResult<Unit> {
        return executeUnit("delete", path) { service ->
            service.delete(path, recursive)
        }
    }

    override suspend fun mkdirs(path: String): NativeFileResult<Unit> {
        return executeUnit("mkdirs", path) { service ->
            service.mkdirs(path)
        }
    }

    override suspend fun copy(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean,
    ): NativeFileResult<Unit> {
        return executeUnit("copy", sourcePath, targetPath) { service ->
            service.copy(sourcePath, targetPath, overwrite)
        }
    }

    override suspend fun move(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean,
    ): NativeFileResult<Unit> {
        return executeUnit("move", sourcePath, targetPath) { service ->
            service.move(sourcePath, targetPath, overwrite)
        }
    }

    override suspend fun chmod(path: String, mode: String): NativeFileResult<Unit> {
        return executeUnit("chmod", path) { service ->
            service.chmod(path, mode)
        }
    }

    override suspend fun length(path: String): NativeFileResult<Long> {
        return execute(
            operation = "length",
            primaryPath = path,
            transform = { bundle -> bundle.getLong(NativeFileBundles.KEY_LONG) },
            block = { service -> service.length(path) },
        )
    }

    override suspend fun readCpuCycles(coreIndex: Int): NativeFileResult<Long> {
        return execute(
            operation = "readCpuCycles",
            primaryPath = "cpu$coreIndex",
            transform = { bundle -> bundle.getLong(NativeFileBundles.KEY_LONG) },
            block = { service -> service.readCpuCycles(coreIndex) },
        )
    }

    override suspend fun unzipFromUri(uriString: String, targetDir: String): NativeFileResult<Unit> {
        return executeUnit("unzipFromUri", uriString, targetDir) { service ->
            val uri = uriString.toUri()
            val pfd = application.contentResolver.openFileDescriptor(uri, "r")
                ?: throw RuntimeException("Cannot open file descriptor from URI: $uriString")
            service.unzipToDir(pfd, targetDir)
        }
    }

    override suspend fun unzipFromPath(sourcePath: String, targetDir: String): NativeFileResult<Unit> {
        return executeUnit("unzipFromPath", sourcePath, targetDir) { service ->
            service.unzipPathToDir(sourcePath, targetDir)
        }
    }

    override suspend fun execDetached(command: String): NativeFileResult<Unit> {
        return executeUnit("execDetached", command) { service ->
            service.execDetached(command)
        }
    }

    override suspend fun startTweakServerEmbedded(packageName: String): NativeFileResult<Unit> {
        return executeUnit("startTweakServerEmbedded", packageName) { service ->
            service.startTweakServerEmbedded(packageName)
        }
    }

    override suspend fun stopTweakServerEmbedded(): NativeFileResult<Unit> {
        return executeUnit("stopTweakServerEmbedded", "") { service ->
            service.stopTweakServerEmbedded()
        }
    }

    suspend fun <T> execute(
        operation: String,
        primaryPath: String,
        secondaryPath: String? = null,
        transform: (Bundle) -> T,
        block: (IRootFileService) -> Bundle,
    ): NativeFileResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                val bundle = block(ShizukuFileServiceConnectionManager.getService())
                bundle.classLoader = javaClass.classLoader
                if (!bundle.getBoolean(NativeFileBundles.KEY_SUCCESS)) {
                    NativeFileResult.Failure(
                        NativeFileError(
                            backend = backend,
                            operation = operation,
                            primaryPath = primaryPath,
                            secondaryPath = secondaryPath,
                            message = bundle.getString(NativeFileBundles.KEY_MESSAGE)
                                ?: "Unknown native file error",
                        )
                    )
                } else {
                    NativeFileResult.Success(transform(bundle))
                }
            } catch (throwable: Throwable) {
                NativeFileResult.Failure(
                    NativeFileError(
                        backend = backend,
                        operation = operation,
                        primaryPath = primaryPath,
                        secondaryPath = secondaryPath,
                        message = throwable.message ?: throwable.toString(),
                    )
                )
            }
        }
    }

    suspend fun executeUnit(
        operation: String,
        primaryPath: String,
        secondaryPath: String? = null,
        block: (IRootFileService) -> Bundle,
    ): NativeFileResult<Unit> {
        return execute(
            operation = operation,
            primaryPath = primaryPath,
            secondaryPath = secondaryPath,
            transform = { },
            block = block,
        )
    }
}

/**
 * 从特权服务通过 PFD 流式回传的目录列表中解析 [FileEntry]。
 *
 * 行格式：`D\t绝对路径` / `F\t绝对路径`。
 * 使用 FD 流式读取可避免大量子项时 Binder 回包超限。
 */
private fun Bundle.toFileEntries(): List<FileEntry> {
    val pfd = getParcelableCompat<ParcelFileDescriptor>(NativeFileBundles.KEY_FD)
        ?: return emptyList()

    return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val separator = line.indexOf('\t')
            if (separator <= 0 || separator >= line.lastIndex) return@mapNotNull null
            val type = line[0]
            if (type != 'D' && type != 'F') return@mapNotNull null
            val path = line.substring(separator + 1)
            if (path.isEmpty()) return@mapNotNull null
            FileEntry(
                path = path,
                name = path.substringAfterLast('/').ifBlank { path },
                isDirectory = type == 'D',
            )
        }.toList()
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Bundle.getParcelableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        getParcelable(key) as? T
    }
}

private fun Bundle.toZipEntries(): List<ZipEntry> {
    return getParcelableArrayListCompat(NativeFileBundles.KEY_BUNDLE_LIST).orEmpty().map { entryBundle ->
        ZipEntry(
            name = entryBundle.getString(NativeFileBundles.KEY_NAME).orEmpty(),
            isDirectory = entryBundle.getBoolean(NativeFileBundles.KEY_IS_DIRECTORY),
            size = entryBundle.getLong(NativeFileBundles.KEY_SIZE),
            compressedSize = entryBundle.getLong(NativeFileBundles.KEY_COMPRESSED_SIZE),
            crc = entryBundle.getLong(NativeFileBundles.KEY_CRC),
            time = entryBundle.getLong(NativeFileBundles.KEY_TIME),
            offset = entryBundle.getLong(NativeFileBundles.KEY_OFFSET),
        )
    }
}

@Suppress("DEPRECATION")
private fun Bundle.getParcelableArrayListCompat(key: String): List<Bundle>? {
    val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(key, Bundle::class.java)
    } else {
        getParcelableArrayList(key)
    }

    return list?.filterIsInstance<Bundle>()
}
