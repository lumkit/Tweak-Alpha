package io.github.lumkit.tweak.common.daemon

import android.os.ParcelFileDescriptor
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileBackend
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.PrivilegedWorkDir
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.openPrivilegedReadOnlyFd
import io.github.lumkit.tweak.common.utils.openPrivilegedWriteOnlyFd
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.asNativeFileBackend
import io.github.lumkit.tweak.server.TweakServerMain
import io.github.lumkit.tweak.server.ipc.TweakServerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "TweakDaemon"
private const val STARTER_SO_NAME = DaemonPaths.STARTER_BIN_NAME

/**
 * C2：工作区安装 starter +（按需）缓存 server.apk，再 fork 独立 `tweak_server`。
 *
 * 分层：
 * - [install]：幂等准备产物（starter 按 sha、server.apk 按版本戳，绝非每次整包复制）
 * - [start]：健康则直接返回；否则只负责拉起进程 + 等 Binder
 */
actual object TweakDaemon {

    @Volatile
    private var cachedAssetSha256: String? = null

    actual suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val paths = DaemonPaths.resolve()
            ensureDaemonDir(paths)
            ensureStarterInstalled(paths)
            ensureServerApkCached(paths)
            true
        }.onFailure {
            logE("install failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // 已有独立常驻进程：零 I/O 快路径
            if (pingInternal() && shellServerAlive()) return@runCatching true
            if (!install()) return@runCatching false
            if (pingInternal() && shellServerAlive()) return@runCatching true

            val paths = DaemonPaths.resolve()
            // 若之前嵌在 file_service：先停引擎并清掉误写的 pid，再拉独立进程
            runCatching { Files.stopTweakServerEmbedded() }
            val oldPid = readServerPid(paths)
            if (oldPid > 0 && isFileServicePid(oldPid)) {
                Files.delete(paths.serverPid)
                TweakServerConnection.clear()
            } else if (!shellServerAlive()) {
                Files.delete(paths.serverPid)
                TweakServerConnection.clear()
            }

            val launchOut = launchStarter(paths)
            logD("launch output:\n$launchOut", TAG)

            var started = waitUntil({ pingInternal() && shellServerAlive() }, attempts = 30, delayMs = 50)
            if (!started) {
                started = waitUntil({ pingInternal() }, attempts = 20, delayMs = 50)
            }
            if (!started) {
                val alive = isRunningInternal(paths)
                logE(
                    "start timeout; serverPidExists=${Files.exists(paths.serverPid).getOrNull()} " +
                        "procAlive=$alive standalone=${shellServerAlive()} " +
                        "binder=${TweakServerConnection.binder != null}\n$launchOut",
                    null,
                    TAG,
                )
                if (alive || shellServerAlive()) {
                    return@runCatching true
                }
            }
            started
        }.onFailure {
            logE("start failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val paths = DaemonPaths.resolve()
            val stoppedByBinder = runCatching {
                TweakServerConnection.service?.stop()
                true
            }.getOrDefault(false)
            runCatching { Files.stopTweakServerEmbedded() }
            if (!stoppedByBinder) {
                val pid = readServerPid(paths)
                // 嵌入模式下 pid 是 file_service，绝不能杀
                if (pid > 0 && !isFileServicePid(pid)) {
                    ReusableShells.execSync("kill -TERM $pid 2>/dev/null; kill -KILL $pid 2>/dev/null")
                }
                ReusableShells.execSync(
                    "pids=\$(pidof tweak_server 2>/dev/null); " +
                        "if [ -n \"\$pids\" ]; then kill -TERM \$pids 2>/dev/null; kill -KILL \$pids 2>/dev/null; fi",
                )
            }
            waitUntil({ !isRunningInternal(paths) && !pingInternal() }, attempts = 20, delayMs = 100)
            TweakServerConnection.clear()
            Files.delete(paths.serverPid)
            true
        }.onFailure {
            logE("stop failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        pingInternal() || isRunningInternal(DaemonPaths.resolve())
    }

    actual suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        pingInternal()
    }

    actual suspend fun status(): TweakDaemonStatus? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = TweakServerConnection.service?.status()?.trim().orEmpty()
            if (!raw.startsWith("OK")) return@runCatching null
            val map = raw.removePrefix("OK")
                .trim()
                .split(' ')
                .mapNotNull { token ->
                    val idx = token.indexOf('=')
                    if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
                }
                .toMap()
            TweakDaemonStatus(
                running = map["running"] == "1",
                pid = map["pid"]?.toIntOrNull() ?: -1,
                version = map["version"].orEmpty(),
                sock = map["binder"] ?: "binder",
                raw = raw,
            )
        }.onFailure {
            logD("status failed: ${it.message}", TAG)
        }.getOrNull()
    }

    actual suspend fun version(): String? = withContext(Dispatchers.IO) {
        status()?.version?.takeIf { it.isNotBlank() } ?: TweakServerMain.VERSION
    }

    actual suspend fun reloadConfig(): Unit = withContext(Dispatchers.IO) {
        runCatching { TweakServerConnection.service?.reloadConfig() }
        Unit
    }

    private suspend fun ensureDaemonDir(paths: DaemonPaths.Resolved) {
        PrivilegedWorkDir.ensureWritable(
            path = paths.dir,
            workRoot = paths.workRoot,
            mode = paths.dirMode,
        )
        PrivilegedWorkDir.ensureWritable(
            path = paths.batteryLogsDir,
            workRoot = paths.workRoot,
            mode = paths.dirMode,
        )
    }

    private fun resolvePackagedStarter(): String? {
        val dir = application.applicationInfo.nativeLibraryDir ?: return null
        val f = File(dir, STARTER_SO_NAME)
        return f.takeIf { it.isFile && it.length() > 0L }?.absolutePath
    }

    private suspend fun isInstalledStarterPresent(paths: DaemonPaths.Resolved): Boolean {
        val local = File(paths.starterBin)
        if (local.isFile && local.length() > 0L) return true
        if (Files.exists(paths.starterBin).getOrNull() != true) return false
        return (Files.length(paths.starterBin).getOrNull() ?: 0L) > 0L
    }

    private suspend fun hashInstalledStarter(paths: DaemonPaths.Resolved): String? = runCatching {
        val local = File(paths.starterBin)
        if (local.isFile && local.canRead()) {
            return@runCatching hashFile(local)
        }
        val backend = resolvePrivilegedBackend()
        openPrivilegedReadOnlyFd(backend, paths.starterBin).use { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { sha256Hex(it) }
        }
    }.onFailure {
        logE("hash installed starter failed: ${it.message}", it, TAG)
    }.getOrNull()

    private suspend fun copyStarterToWorkDir(paths: DaemonPaths.Resolved, packaged: File) {
        val localDest = File(paths.starterBin)
        // 工作区通常 App 也可写；失败再走特权
        val copiedLocal = runCatching {
            localDest.parentFile?.mkdirs()
            packaged.inputStream().use { input ->
                FileOutputStream(localDest).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            localDest.isFile && localDest.length() > 0L
        }.getOrDefault(false)
        if (copiedLocal) return

        val backend = resolvePrivilegedBackend()
        packaged.inputStream().use { input ->
            openPrivilegedWriteOnlyFd(
                backend = backend,
                path = paths.starterBin,
                create = true,
                truncate = true,
            ).use { writePfd ->
                ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        }
    }

    private suspend fun setBinaryExecutable(binPath: String) {
        runCatching { File(binPath).setExecutable(true, false) }
        Files.chmod(binPath, "0755")
        ReusableShells.execSync("chmod 755 ${binPath.shellQuote()} 2>/dev/null || true")
    }

    private suspend fun deleteBinary(binPath: String) {
        runCatching { File(binPath).delete() }
        runCatching { Files.delete(binPath) }
    }

    private suspend fun ensureStarterInstalled(paths: DaemonPaths.Resolved) {
        val packaged = resolvePackagedStarter()
            ?: error("$STARTER_SO_NAME missing in nativeLibraryDir (APK 未打包 starter)")
        val expectedSha = hashFile(File(packaged))
            ?: error("cannot hash packaged starter")
        cachedAssetSha256 = expectedSha

        if (isInstalledStarterPresent(paths)) {
            val actualSha = hashInstalledStarter(paths)
            if (actualSha != null && actualSha.equals(expectedSha, ignoreCase = true)) {
                setBinaryExecutable(paths.starterBin)
                logD("starter already installed sha=$actualSha", TAG)
                return
            }
            logD("starter outdated/corrupt actual=$actualSha, reinstall", TAG)
            deleteBinary(paths.starterBin)
        }

        copyStarterToWorkDir(paths, File(packaged))
        setBinaryExecutable(paths.starterBin)
        val after = hashInstalledStarter(paths)
        if (after == null || !after.equals(expectedSha, ignoreCase = true)) {
            deleteBinary(paths.starterBin)
            error("starter integrity failed expected=$expectedSha actual=$after")
        }
        logD("installed starter -> ${paths.starterBin} sha=$after", TAG)
    }

    /**
     * 仅在 App 升级/换路径时同步 server.apk。
     * 戳：sourceDir|versionCode|lastUpdateTime|length —— O(1) 判断，禁止每次冷启整包 cp。
     */
    private suspend fun ensureServerApkCached(paths: DaemonPaths.Resolved) {
        val stamp = currentApkStamp()
        val stampText = stamp.serialize()
        val apkFile = File(paths.serverApk)
        val stampFile = File(paths.serverApkStamp)

        val cachedStamp = runCatching {
            when {
                stampFile.canRead() -> stampFile.readText().trim()
                else -> Files.readText(paths.serverApkStamp).getOrNull()?.trim().orEmpty()
            }
        }.getOrDefault("")

        val cachedLen = when {
            apkFile.isFile -> apkFile.length()
            else -> Files.length(paths.serverApk).getOrNull() ?: -1L
        }
        if (cachedStamp == stampText && cachedLen == stamp.length && cachedLen > 0L) {
            logD("server.apk cache hit stamp=$stampText", TAG)
            return
        }

        logD(
            "server.apk cache miss oldStamp=${cachedStamp.take(80)} newStamp=$stampText, syncing",
            TAG,
        )
        // 优先硬链（同分区瞬时完成）；失败再 cp 一次
        val sync = ReusableShells.execSync(
            "rm -f ${paths.serverApk.shellQuote()} ${paths.serverApkStamp.shellQuote()}; " +
                "ln ${stamp.sourceDir.shellQuote()} ${paths.serverApk.shellQuote()} 2>/dev/null || " +
                "cp -f ${stamp.sourceDir.shellQuote()} ${paths.serverApk.shellQuote()}; " +
                "chmod 644 ${paths.serverApk.shellQuote()}; " +
                "printf '%s\\n' ${stampText.shellQuote()} > ${paths.serverApkStamp.shellQuote()}; " +
                "chmod 644 ${paths.serverApkStamp.shellQuote()}; " +
                "stat -c '%s' ${paths.serverApk.shellQuote()} 2>/dev/null || " +
                "wc -c < ${paths.serverApk.shellQuote()}",
        ).trim().lines().lastOrNull()?.trim().orEmpty()
        val afterLen = sync.toLongOrNull() ?: -1L
        if (afterLen != stamp.length) {
            error("server.apk sync failed expectedLen=${stamp.length} actualLen=$afterLen out=$sync")
        }
        logD("server.apk synced len=$afterLen via install", TAG)
    }

    private fun currentApkStamp(): ApkStamp {
        val sourceDir = application.applicationInfo.sourceDir
        val pkgInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pkgInfo.longVersionCode
        } else {
            pkgInfo.versionCode.toLong()
        }
        val length = File(sourceDir).length()
        return ApkStamp(
            sourceDir = sourceDir,
            versionCode = versionCode,
            lastUpdateTime = pkgInfo.lastUpdateTime,
            length = length,
        )
    }

    private data class ApkStamp(
        val sourceDir: String,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val length: Long,
    ) {
        fun serialize(): String = "$sourceDir|$versionCode|$lastUpdateTime|$length"
    }

    private suspend fun launchStarter(paths: DaemonPaths.Resolved): String {
        val pkgName = application.packageName
        val starter = paths.starterBin
        val serverApk = paths.serverApk
        val bootLog = "${paths.dir}/starter.boot.log"

        require(File(serverApk).isFile || Files.exists(serverApk).getOrNull() == true) {
            "server.apk missing, install() should have prepared it"
        }

        ReusableShells.execSync("chmod 755 ${starter.shellQuote()} 2>/dev/null || true")
        ReusableShells.execSync("rm -f ${bootLog.shellQuote()} 2>/dev/null || true")

        val envPrefix =
            "export ANDROID_DATA=/data ANDROID_ROOT=/system " +
                "PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin; " +
                "unset LD_LIBRARY_PATH CLASSPATH ANDROID_SOCKET_zygote ANDROID_ENTRYPOINT;"
        val apkQ = serverApk.shellQuote()
        val pkgQ = pkgName.shellQuote()
        val starterQ = starter.shellQuote()

        // 1) 独立进程：后台 setsid，脱离 App 生命周期（不再在此复制 APK）
        val bg = ReusableShells.execSync(
            "$envPrefix " +
                "setsid $starterQ --apk=$apkQ --package=$pkgQ " +
                ">>${bootLog.shellQuote()} 2>&1 < /dev/null & " +
                "echo __BG_PID:\$!; " +
                "echo __PIDOF:\$(pidof tweak_server 2>/dev/null)",
        )
        if (waitUntil({ pingInternal() && shellServerAlive() }, attempts = 30, delayMs = 50)) {
            return "mode=standalone_bg\n$bg"
        }
        if (shellServerAlive()) {
            val binderOk = waitUntil({ pingInternal() }, attempts = 20, delayMs = 50)
            return "mode=standalone_alive binder=$binderOk\n$bg\n${readBootLog(bootLog)}"
        }

        // 2) file_service 旁路拉起
        val startCmd = "$envPrefix $starterQ --apk=$apkQ --package=$pkgQ"
        val viaService = runCatching {
            when (val r = Files.execDetached(startCmd)) {
                is NativeFileResult.Success -> "execDetached=ok"
                is NativeFileResult.Failure -> "execDetached=fail:${r.error.message}"
            }
        }.getOrElse { "execDetached=error:${it.message}" }
        if (waitUntil({ pingInternal() && shellServerAlive() }, attempts = 25, delayMs = 50)) {
            return "mode=exec_detached\n$viaService\n$bg"
        }

        // 3) 最后回退嵌入（强停可能一起没）
        val embedded = runCatching {
            when (val r = Files.startTweakServerEmbedded(pkgName)) {
                is NativeFileResult.Success -> "embedded=ok"
                is NativeFileResult.Failure -> "embedded=fail:${r.error.message}"
            }
        }.getOrElse { "embedded=error:${it.message}" }
        if (waitUntil({ pingInternal() }, attempts = 20, delayMs = 50)) {
            return "mode=embedded_fallback\n$embedded\n$viaService\n$bg"
        }

        return "mode=all_failed dir=${paths.dir}\n$embedded\n$viaService\n$bg\n${readBootLog(bootLog)}"
    }

    private suspend fun readBootLog(bootLog: String): String =
        runCatching {
            ReusableShells.execSync("cat ${bootLog.shellQuote()} 2>/dev/null || true")
        }.getOrDefault("")

    private suspend fun isFileServicePid(pid: Int): Boolean {
        if (pid <= 0) return false
        val cmd = runCatching {
            ReusableShells.execSync("tr '\\0' ' ' < /proc/$pid/cmdline 2>/dev/null || true")
        }.getOrDefault("")
        return cmd.contains("file_service")
    }

    private fun pingInternal(): Boolean =
        runCatching { TweakServerConnection.service?.ping() == "PONG" }.getOrDefault(false)

    private suspend fun shellServerAlive(): Boolean {
        val out = runCatching {
            ReusableShells.execSync("pidof tweak_server 2>/dev/null || true")
        }.getOrDefault("").trim()
        return out.split(Regex("\\s+")).any { token ->
            token.toIntOrNull()?.let { it > 0 } == true
        }
    }

    private suspend fun isRunningInternal(paths: DaemonPaths.Resolved): Boolean {
        if (shellServerAlive()) return true
        val pid = readServerPid(paths)
        if (pid <= 0) return false
        return File("/proc/$pid").exists() || Files.exists("/proc/$pid").getOrNull() == true
    }

    private suspend fun readServerPid(paths: DaemonPaths.Resolved): Int {
        val text = Files.readText(paths.serverPid).getOrNull()
            ?.trim()
            ?.lines()
            ?.firstOrNull()
            .orEmpty()
        return text.toIntOrNull() ?: -1
    }

    private suspend fun resolvePrivilegedBackend(): NativeFileBackend {
        val runtimeMode = GlobalViewModel.runtimeModeState.filterNotNull().first()
        val backend = runtimeMode.asNativeFileBackend()
        require(backend != NativeFileBackend.User) {
            "当前运行模式不支持安装 starter"
        }
        return backend
    }

    private fun hashFile(file: File): String? =
        runCatching { FileInputStream(file).use { sha256Hex(it) } }.getOrNull()

    private fun sha256Hex(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) continue
            digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private suspend fun waitUntil(
        condition: suspend () -> Boolean,
        attempts: Int,
        delayMs: Long,
    ): Boolean {
        repeat(attempts) {
            if (condition()) return true
            delay(delayMs.milliseconds)
        }
        return condition()
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
}
