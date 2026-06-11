package io.github.lumkit.tweak.common.utils

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.sharednative.IRootFileService
import io.github.lumkit.tweak.sharednative.NativeFileBundles
import io.github.lumkit.tweak.sharednative.RootFileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual object PlatformNativeFileServices : NativeFileServiceProvider {
    private val rootService = RootNativeFileService

    override fun getOrNull(backend: NativeFileBackend): NativeFileService? {
        return when (backend) {
            NativeFileBackend.ROOT -> rootService
            NativeFileBackend.SHIZUKU -> null
            NativeFileBackend.User -> null
        }
    }
}

private object RootFileServiceConnectionManager {
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

    private suspend fun <T> execute(
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

    private suspend fun executeUnit(
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
