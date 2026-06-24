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
    private var extraEnvPath: String = ""
    private var defaultEnvPath: String = "" // /sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin
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

    fun setExtraEnvPath(extraEnvPath: String) {
        ShellExecutor.extraEnvPath = extraEnvPath
    }

    private fun getEnvPath(): String? {
        // FIXME:非root模式下，默认的 TMPDIR=/data/local/tmp 变量可能会导致某些需要写缓存的场景（例如使用source指令）脚本执行失败！
        if (!extraEnvPath.isEmpty()) {
            if (defaultEnvPath.isEmpty()) {
                try {
                    val process = Runtime.getRuntime().exec("sh")
                    val outputStream = process.outputStream
                    outputStream.write($$"echo $PATH".toByteArray())
                    outputStream.flush()
                    outputStream.close()

                    val inputStream = process.inputStream
                    val cache = ByteArray(16384)
                    val length = inputStream.read(cache)
                    inputStream.close()
                    process.destroy()

                    val path = String(cache, 0, length).trim { it <= ' ' }
                    if (path.isNotEmpty()) {
                        defaultEnvPath = path
                    } else {
                        throw RuntimeException("未能获取到\$PATH参数")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    defaultEnvPath = "/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
                }
            }

            val path = defaultEnvPath

            return "PATH=$path:$extraEnvPath"
        }

        return null
    }

    @Throws(IOException::class)
    private fun getProcess(run: String?): Process {
        val env = getEnvPath()
        val runtime = Runtime.getRuntime()
        /*
        // 部分机型会有Aborted错误
        if (env != null) {
            return runtime.exec(run, new String[]{
                env
            });
        }
        */


        val process = runtime.exec(run)
        if (env != null) {
            val outputStream = process.outputStream
            outputStream.write("export ".toByteArray())
            outputStream.write(env.toByteArray())
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
    fun getSuperUserRuntime(): Process {
        val userId = resolveSuperUserId()
        logD("user id=$userId", TAG)
        return getProcess(userId)
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

        val env = getEnvPath()?.let(::arrayOf)
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
