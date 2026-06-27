package io.github.lumkit.tweak.common.shell

import android.content.pm.PackageManager
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku
import java.io.IOException
import java.lang.reflect.Method


object ShellExecutor {
    private const val TAG = "ShellExecutor"
    private val shizukuNewProcessMethod: Method by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply {
            isAccessible = true
        }
    }
    private var workPath: String = ""
    private var defaultWorkPath: String = ""

    private fun smartWorkPath(): String? {
        if (workPath.isNotBlank()) {
            if (defaultWorkPath.isBlank()) {
                defaultWorkPath = try {
                    Runtime.getRuntime().exec("sh").let {
                        it.outputStream.use { os ->
                            os.write("echo \$PATH".toByteArray())
                            os.flush()
                        }

                        it.inputStream.use { input ->
                            val cache = ByteArray(16384)
                            val len = input.read(cache)
                            String(cache, 0, len).trim().ifBlank {
                                throw RuntimeException("未能获取到\$PATH参数")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    "/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
                }
            }

            return "PATH=$defaultWorkPath:$workPath"
        }
        return null
    }

    @Throws(IOException::class)
    private fun getProcess(run: String?, redirectErrorStream: Boolean = false): Process {
        val path = smartWorkPath()
        val process = ProcessBuilder()
            .command(run)
            .redirectErrorStream(redirectErrorStream)
            .start()
        if (path != null) {
            val outputStream = process.outputStream
            outputStream.write("export ".toByteArray())
            outputStream.write(path.toByteArray())
            outputStream.write("\n".toByteArray())
            outputStream.flush()
        }
        return process
    }

    @Throws(IOException::class)
    fun resolveSuperUserId(): String {
        val userIdCache = runBlocking { TweakDataStore.keepShellUserIdFlow().firstOrNull() }

        if (userIdCache.isNullOrBlank()) {
            logD("开始检测su命令", TAG)
            val process = getProcess("sh")
            return try {
                process.outputStream.use { outputStream ->
                    outputStream.write("which su\n".toByteArray())
                    outputStream.write("exit\n".toByteArray())
                    outputStream.flush()
                }

                val result = process.inputStream.bufferedReader().use { it.readText().trim() }
                logD("resolveSuperUserId: $result", TAG)
                process.waitFor()
                val userId = if (result.endsWith("/su")) "su" else "suu"
                runBlocking { TweakDataStore.setKeepShellUserId(userId) }
                userId
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while resolving super user executable", e)
            } finally {
                process.errorStream.close()
                process.destroy()
            }
        } else {
            logD("userIdCache=$userIdCache", TAG)
            return userIdCache
        }
    }

    @Throws(IOException::class)
    fun getSuperUserRuntime(redirectErrorStream: Boolean = false): Process {
        val userId = resolveSuperUserId()
        logD("user id=$userId", TAG)
        return getProcess(userId, redirectErrorStream)
    }

    @Throws(IOException::class)
    fun getRuntime(): Process {
        return getProcess("sh")
    }

    @Throws(IOException::class)
    fun getShizukuRuntime(): Process {
        if (!Shizuku.pingBinder()) {
            throw IOException("Shizuku service is not running")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw IOException("Shizuku permission not granted")
        }

        val env = smartWorkPath()?.let(::arrayOf)
        return try {
            val process = shizukuNewProcessMethod.invoke(
                null,
                arrayOf("sh"),
                env,
                null
            )
            process as? Process ?: throw IOException("Shizuku newProcess returned invalid process")
        } catch (e: IOException) {
            throw e
        } catch (e: ReflectiveOperationException) {
            throw IOException("Failed to invoke Shizuku.newProcess via reflection", e)
        } catch (e: SecurityException) {
            throw IOException("Shizuku newProcess is not accessible", e)
        }
    }
}
