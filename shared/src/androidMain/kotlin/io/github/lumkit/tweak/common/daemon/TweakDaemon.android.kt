package io.github.lumkit.tweak.common.daemon

import android.os.ParcelFileDescriptor
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.daemon.TweakDaemon.install
import io.github.lumkit.tweak.common.daemon.TweakDaemon.start
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileBackend
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.PrivilegedWorkDir
import io.github.lumkit.tweak.common.utils.TweakDataStore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "TweakDaemon"
private const val STARTER_SO_NAME = DaemonPaths.STARTER_BIN_NAME
private const val STARTER_SHA_SIDECAR = "libtweak_starter.so.sha256"

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

    @Volatile
    private var cachedPackagedStarterSha: String? = null

    @Volatile
    private var installFastPathKey: String? = null

    @Volatile
    private var cachedPrivilegedBackend: NativeFileBackend? = null

    private val startMutex = Mutex()

    actual suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val paths = DaemonPaths.resolve()
            if (installFastPathKey == paths.dir && quickArtifactsReady(paths)) {
                logD("install fast path", TAG)
                return@runCatching true
            }
            // 产物过期/残缺时先停进程并清空工作区（保留 battery_logs），
            // 避免 Shizuku 无法完整覆写 Root/旧权限留下的 server.apk 等文件。
            if (!quickArtifactsReady(paths)) {
                stopRunningServerForReinstall(paths)
                clearDaemonArtifactsPreservingLogs(paths)
                installFastPathKey = null
            }
            if (!daemonDirsLookReady(paths)) {
                ensureDaemonDir(paths)
            } else {
                PrivilegedWorkDir.ensureWritable(
                    path = paths.batteryLogsDir,
                    workRoot = paths.workRoot,
                    mode = paths.dirMode,
                )
            }
            ensureStarterInstalled(paths)
            ensureServerApkCached(paths)
            installFastPathKey = paths.dir
            true
        }.onFailure {
            logE("install failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun artifactsOutdated(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val paths = DaemonPaths.resolve()
            !quickArtifactsReady(paths)
        }.getOrDefault(true)
    }

    actual suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        startMutex.withLock {
            startLocked()
        }
    }

    private suspend fun startLocked(): Boolean =
        runCatching {
            val paths = DaemonPaths.resolve()

            // App 升级后工作区 starter/server.apk 可能过期：必须先停再装，禁止沿用旧 app_process
            if (!quickArtifactsReady(paths)) {
                logD("artifacts outdated before start, stop + reinstall", TAG)
                installFastPathKey = null
                stopRunningServerForReinstall(paths)
                if (!install()) return@runCatching false
            }

            if (pingInternal()) return@runCatching true

            // 独立 tweak_server 已在跑：只等 Binder 重投递，禁止再 fork（冷启动常见）
            if (standaloneServerAlive(paths)) {
                logD("tweak_server already alive, wait for binder attach", TAG)
                waitForBinderAttach(attempts = 48, delayMs = 25)
                return@runCatching pingInternal() || standaloneServerAlive(paths)
            }

            if (!install()) return@runCatching false

            if (pingInternal()) return@runCatching true
            if (standaloneServerAlive(paths)) {
                waitForBinderAttach(attempts = 32, delayMs = 25)
                return@runCatching pingInternal() || standaloneServerAlive(paths)
            }

            val oldPid = readServerPid(paths)
            if (oldPid > 0 && isFileServicePid(oldPid)) {
                Files.delete(paths.serverPid)
                TweakServerConnection.clear()
            } else if (!standaloneServerAlive(paths)) {
                Files.delete(paths.serverPid)
                TweakServerConnection.clear()
            }

            if (standaloneServerAlive(paths)) {
                logD("tweak_server appeared before launch, skip starter", TAG)
                waitForBinderAttach(attempts = 32, delayMs = 25)
                return@runCatching pingInternal() || standaloneServerAlive(paths)
            }

            runCatching { Files.stopTweakServerEmbedded() }

            val launchOut = launchStarter(paths)
            logD("launch output:\n$launchOut", TAG)

            var started = waitUntil({ pingInternal() && standaloneServerAlive(paths) }, attempts = 40, delayMs = 25)
            if (!started) {
                started = waitUntil({ pingInternal() }, attempts = 24, delayMs = 25)
            }
            if (!started) {
                val alive = standaloneServerAlive(paths)
                logE(
                    "start timeout; serverPidExists=${Files.exists(paths.serverPid).getOrNull()} " +
                        "procAlive=$alive binder=${TweakServerConnection.binder != null}\n$launchOut",
                    null,
                    TAG,
                )
                if (alive) {
                    return@runCatching true
                }
            }
            started
        }.onFailure {
            logE("start failed: ${it.message}", it, TAG)
        }.getOrDefault(false)

    actual suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val paths = DaemonPaths.resolve()
            val hadStandalone = standaloneServerAlive(paths)
            // 优先 Binder 自退出：Root 拉起的 tweak_server 在切到 Shizuku 后，shell kill 常无效
            val svc = TweakServerConnection.service
            val binderStopSent = if (svc != null) {
                runCatching {
                    svc.stop()
                    logD("sent binder stop to tweak_server", TAG)
                    true
                }.onFailure {
                    logE("binder stop failed: ${it.message}", it, TAG)
                }.getOrDefault(false)
            } else {
                logD("no binder; cannot request self-stop", TAG)
                false
            }

            runCatching { Files.stopTweakServerEmbedded() }

            // embedded：停引擎后进程仍在，但 binder ping 应变为失败；尽快清本地连接
            var exited = waitUntil(
                condition = { !pingInternal() && !standaloneServerAlive(paths) },
                attempts = if (binderStopSent) 40 else 5,
                delayMs = 50,
            )

            if (!exited && binderStopSent && !hadStandalone && !standaloneServerAlive(paths)) {
                // 仅嵌入式：无独立 tweak_server，Binder 已请求停止 → 清连接即视为成功
                TweakServerConnection.clear()
                if (!pingInternal()) {
                    logD("embedded stop accepted after binder stop", TAG)
                    exited = true
                }
            }

            if (!exited) {
                logD("daemon still alive after binder/embedded stop, try shell kill fallback", TAG)
                val pid = readServerPid(paths)
                if (pid > 0 && !isFileServicePid(pid)) {
                    ReusableShells.execSync("kill -TERM $pid 2>/dev/null; kill -KILL $pid 2>/dev/null")
                }
                ReusableShells.execSync(
                    "pids=\$(pidof tweak_server 2>/dev/null); " +
                        "if [ -n \"\$pids\" ]; then kill -TERM \$pids 2>/dev/null; kill -KILL \$pids 2>/dev/null; fi",
                )
                exited = waitUntil(
                    condition = { !pingInternal() && !standaloneServerAlive(paths) },
                    attempts = 20,
                    delayMs = 100,
                )
            }

            TweakServerConnection.clear()
            Files.delete(paths.serverPid)
            // 清连接后再确认一次：embedded 场景下本地无 binder 即算停干净
            if (!exited && !standaloneServerAlive(paths) && !pingInternal()) {
                exited = true
            }
            if (!exited) {
                logE(
                    "stop incomplete: standalone tweak_server may still be running " +
                        "(e.g. Root daemon under Shizuku)",
                    null,
                    TAG,
                )
            }
            exited
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

    /**
     * 清空 daemon 工作区安装产物，**保留** [DaemonPaths.BATTERY_LOGS_DIR_NAME] 采样记录。
     * 用于重装前规避「无法覆写旧文件」导致的 server.apk 长度校验失败。
     */
    private suspend fun clearDaemonArtifactsPreservingLogs(paths: DaemonPaths.Resolved) {
        val preserve = DaemonPaths.BATTERY_LOGS_DIR_NAME
        logD("clear daemon artifacts, preserve=$preserve dir=${paths.dir}", TAG)
        forceChmod777(paths.workRoot, paths.dir)

        val localDir = File(paths.dir)
        if (localDir.isDirectory) {
            localDir.listFiles()?.forEach { child ->
                if (child.name == preserve) return@forEach
                forceChmod777(child.absolutePath)
                runCatching {
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
        }

        // 特权壳再清一遍（覆盖 App 无权限删除的 root/shell 属主文件）
        ReusableShells.execSync(
            "d=${paths.dir.shellQuote()}; " +
                "preserve=${preserve.shellQuote()}; " +
                "chmod 777 \"\$d\" 2>/dev/null || true; " +
                "if [ -d \"\$d\" ]; then " +
                "for f in \"\$d\"/* \"\$d\"/.[!.]* \"\$d\"/..?*; do " +
                "[ -e \"\$f\" ] || continue; " +
                "b=\$(basename \"\$f\"); " +
                "[ \"\$b\" = \"\$preserve\" ] && continue; " +
                "chmod -R 777 \"\$f\" 2>/dev/null || true; " +
                "rm -rf \"\$f\"; " +
                "done; " +
                "fi",
        )

        val listed = Files.list(paths.dir).getOrNull().orEmpty()
        for (name in listed) {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
            if (base.isEmpty() || base == preserve || base == "." || base == "..") continue
            val path = if (name.startsWith(paths.dir)) name else "${paths.dir}/$base"
            forceChmod777(path)
            runCatching { Files.delete(path, recursive = true) }
        }
    }

    private suspend fun daemonDirsLookReady(paths: DaemonPaths.Resolved): Boolean {
        return Files.exists(paths.dir).getOrNull() == true &&
            Files.exists(paths.batteryLogsDir).getOrNull() == true
    }

    private suspend fun quickArtifactsReady(paths: DaemonPaths.Resolved): Boolean {
        if (!isInstalledStarterPresent(paths)) return false
        val expectedSha = packagedStarterSha() ?: return false
        val sidecar = starterShaSidecarPath(paths)
        val recorded = readTextQuick(sidecar)?.trim().orEmpty()
        if (!recorded.equals(expectedSha, ignoreCase = true)) return false

        val stampText = currentApkStamp().serialize()
        val cachedStamp = readTextQuick(paths.serverApkStamp)?.trim().orEmpty()
        if (cachedStamp != stampText) return false
        val serverApk = resolveActiveServerApkPath(paths)
        val len = File(serverApk).takeIf { it.isFile }?.length()
            ?: Files.length(serverApk).getOrNull()
            ?: -1L
        return len == currentApkStamp().length && len > 0L
    }

    private fun starterShaSidecarPath(paths: DaemonPaths.Resolved): String =
        "${paths.dir}/$STARTER_SHA_SIDECAR"

    private suspend fun readTextQuick(path: String): String? {
        val local = File(path)
        if (local.isFile && local.canRead()) {
            return runCatching { local.readText() }.getOrNull()
        }
        return Files.readText(path).getOrNull()
    }

    private suspend fun writeStarterShaSidecar(paths: DaemonPaths.Resolved, sha: String) {
        val path = starterShaSidecarPath(paths)
        runCatching { File(path).writeText("$sha\n") }
        Files.writeText(path, "$sha\n")
    }

    private fun packagedStarterSha(): String? {
        cachedPackagedStarterSha?.let { return it }
        cachedAssetSha256?.let { return it }
        val packaged = resolvePackagedStarter() ?: return null
        return hashFile(File(packaged))?.also {
            cachedPackagedStarterSha = it
            cachedAssetSha256 = it
        }
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
        val backend = resolvePrivilegedBackendCached()
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

        val backend = resolvePrivilegedBackendCached()
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
        val local = File(binPath)
        if (local.isFile && local.canExecute()) {
            return
        }
        runCatching { local.setExecutable(true, false) }
        Files.chmod(binPath, "0755")
        ReusableShells.execSync("chmod 755 ${binPath.shellQuote()} 2>/dev/null || true")
    }

    private suspend fun deleteBinary(binPath: String) {
        runCatching { File(binPath).delete() }
        runCatching { Files.delete(binPath) }
    }

    /**
     * 重装产物前停掉独立 tweak_server（嵌入 file_service 用 stopEmbedded，不杀父进程）。
     */
    private suspend fun stopRunningServerForReinstall(paths: DaemonPaths.Resolved) {
        val alive = pingInternal() || standaloneServerAlive(paths)
        if (!alive) {
            runCatching { Files.stopTweakServerEmbedded() }
            TweakServerConnection.clear()
            return
        }
        logD("stopping running daemon before artifact reinstall", TAG)
        runCatching {
            TweakServerConnection.service?.stop()
        }
        runCatching { Files.stopTweakServerEmbedded() }
        val pid = readServerPid(paths)
        if (pid > 0 && !isFileServicePid(pid)) {
            ReusableShells.execSync("kill -TERM $pid 2>/dev/null; kill -KILL $pid 2>/dev/null")
        }
        ReusableShells.execSync(
            "pids=\$(pidof tweak_server 2>/dev/null); " +
                "if [ -n \"\$pids\" ]; then kill -TERM \$pids 2>/dev/null; kill -KILL \$pids 2>/dev/null; fi",
        )
        waitUntil({ !standaloneServerAlive(paths) && !pingInternal() }, attempts = 30, delayMs = 50)
        TweakServerConnection.clear()
        Files.delete(paths.serverPid)
    }

    private suspend fun ensureStarterInstalled(paths: DaemonPaths.Resolved) {
        val packaged = resolvePackagedStarter()
            ?: error("$STARTER_SO_NAME missing in nativeLibraryDir (APK 未打包 starter)")
        val expectedSha = packagedStarterSha()
            ?: error("cannot hash packaged starter")

        if (isInstalledStarterPresent(paths)) {
            val sidecarSha = readTextQuick(starterShaSidecarPath(paths))?.trim()
            if (sidecarSha.equals(expectedSha, ignoreCase = true)) {
                setBinaryExecutable(paths.starterBin)
                logD("starter sidecar hit sha=$sidecarSha", TAG)
                return
            }
            val actualSha = hashInstalledStarter(paths)
            if (actualSha != null && actualSha.equals(expectedSha, ignoreCase = true)) {
                writeStarterShaSidecar(paths, expectedSha)
                setBinaryExecutable(paths.starterBin)
                logD("starter already installed sha=$actualSha", TAG)
                return
            }
            logD("starter outdated/corrupt actual=$actualSha, reinstall", TAG)
            // 二进制被替换前先停进程，避免仍在执行旧 starter / 映射旧 dex
            stopRunningServerForReinstall(paths)
            deleteBinary(paths.starterBin)
            deleteBinary(starterShaSidecarPath(paths))
        }

        copyStarterToWorkDir(paths, File(packaged))
        setBinaryExecutable(paths.starterBin)
        val after = hashInstalledStarter(paths)
        if (after == null || !after.equals(expectedSha, ignoreCase = true)) {
            deleteBinary(paths.starterBin)
            error("starter integrity failed expected=$expectedSha actual=$after")
        }
        writeStarterShaSidecar(paths, expectedSha)
        logD("installed starter -> ${paths.starterBin} sha=$after", TAG)
    }

    private fun serverApkActivePathSidecar(paths: DaemonPaths.Resolved): String =
        "${paths.dir}/server.apk.path"

    private suspend fun resolveActiveServerApkPath(paths: DaemonPaths.Resolved): String {
        val recorded = readTextQuick(serverApkActivePathSidecar(paths))?.trim().orEmpty()
        if (recorded.isNotEmpty() &&
            (File(recorded).isFile || Files.exists(recorded).getOrNull() == true)
        ) {
            return recorded
        }
        return paths.serverApk
    }

    private suspend fun writeActiveServerApkPath(paths: DaemonPaths.Resolved, apkPath: String) {
        val sidecar = serverApkActivePathSidecar(paths)
        runCatching { File(sidecar).writeText("$apkPath\n") }
        runCatching { Files.writeText(sidecar, "$apkPath\n") }
        forceChmod777(paths.dir, sidecar)
    }

    /** 写入前/后强制 0777，规避 Root 残留只读文件与 sticky tmp 权限问题。 */
    private suspend fun forceChmod777(vararg pathsToChmod: String) {
        for (path in pathsToChmod) {
            if (path.isBlank()) continue
            runCatching { File(path).setReadable(true, false) }
            runCatching { File(path).setWritable(true, false) }
            runCatching { File(path).setExecutable(true, false) }
            runCatching { Files.chmod(path, "0777") }
        }
        val quoted = pathsToChmod.filter { it.isNotBlank() }.joinToString(" ") { it.shellQuote() }
        if (quoted.isNotEmpty()) {
            ReusableShells.execSync("chmod 777 $quoted 2>/dev/null || true")
        }
    }

    /**
     * 仅在 App 升级/换路径时同步 server.apk。
     * 戳：sourceDir|versionCode|lastUpdateTime|length —— O(1) 判断，禁止每次冷启整包 cp。
     *
     * Shell 通常读不了 /data/app 下的 base.apk，必须先由 App 进程读出再落到工作区。
     * 写入全程 chmod 0777；若主路径被 Root 残留占用，则落到 uid 后缀旁路文件。
     */
    private suspend fun ensureServerApkCached(paths: DaemonPaths.Resolved) {
        val stamp = currentApkStamp()
        val sourceFile = File(stamp.sourceDir)
        require(sourceFile.isFile && stamp.length > 0L) {
            "source apk missing: ${stamp.sourceDir}"
        }

        forceChmod777(paths.workRoot, paths.dir)
        PrivilegedWorkDir.ensureWritable(
            path = paths.dir,
            workRoot = paths.workRoot,
            mode = "0777",
        )

        val activePath = resolveActiveServerApkPath(paths)
        val stampFile = File(paths.serverApkStamp)
        val cachedStamp = runCatching {
            when {
                stampFile.canRead() -> stampFile.readText().trim()
                else -> Files.readText(paths.serverApkStamp).getOrNull()?.trim().orEmpty()
            }
        }.getOrDefault("")
        val cachedLen = measureFileLength(activePath)
        if (cachedStamp == stamp.serialize() && cachedLen == stamp.length && cachedLen > 0L) {
            logD("server.apk cache hit path=$activePath stamp=${stamp.serialize()}", TAG)
            return
        }

        logD(
            "server.apk cache miss oldStamp=${cachedStamp.take(80)} newStamp=${stamp.serialize()}, syncing",
            TAG,
        )
        stopRunningServerForReinstall(paths)

        val destPath = prepareWritableServerApkDest(paths)
        val copied = copyServerApkToWorkDir(sourcePath = stamp.sourceDir, destPath = destPath)
        val sourceLen = measureFileLength(stamp.sourceDir).takeIf { it > 0L } ?: stamp.length
        val afterLen = measureFileLength(destPath)
        if (!copied || afterLen != sourceLen) {
            error(
                "server.apk sync failed expectedLen=$sourceLen actualLen=$afterLen " +
                    "copied=$copied dest=$destPath source=${stamp.sourceDir}",
            )
        }

        forceChmod777(destPath)
        writeActiveServerApkPath(paths, destPath)

        val finalStamp = stamp.copy(length = afterLen)
        val stampText = finalStamp.serialize()
        runCatching {
            stampFile.parentFile?.mkdirs()
            stampFile.writeText("$stampText\n")
        }
        runCatching { Files.writeText(paths.serverApkStamp, "$stampText\n") }
        forceChmod777(paths.serverApkStamp, serverApkActivePathSidecar(paths))
        ReusableShells.execSync(
            "printf '%s\\n' ${stampText.shellQuote()} > ${paths.serverApkStamp.shellQuote()}; " +
                "chmod 777 ${destPath.shellQuote()} ${paths.serverApkStamp.shellQuote()} " +
                "${serverApkActivePathSidecar(paths).shellQuote()} 2>/dev/null || true",
        )
        logD("server.apk synced len=$afterLen path=$destPath", TAG)
    }

    /**
     * 准备可写的 server.apk 目标：先 0777 + 删主路径；删不掉（Root 残留）则用 uid 旁路文件。
     */
    private suspend fun prepareWritableServerApkDest(paths: DaemonPaths.Resolved): String {
        forceChmod777(paths.dir, paths.serverApk, paths.serverApkStamp)
        forceRemovePath(paths.serverApk)
        forceRemovePath(paths.serverApkStamp)

        val primaryGone = File(paths.serverApk).exists().not() &&
            Files.exists(paths.serverApk).getOrNull() != true
        if (primaryGone) {
            return paths.serverApk
        }

        val alt = "${paths.serverApk}.${android.os.Process.myUid()}"
        logD("primary server.apk still present after chmod/rm, use alt=$alt", TAG)
        forceChmod777(alt)
        forceRemovePath(alt)
        return alt
    }

    private suspend fun forceRemovePath(path: String) {
        forceChmod777(path)
        runCatching { File(path).delete() }
        runCatching { Files.delete(path, recursive = false) }
        ReusableShells.execSync(
            "p=${path.shellQuote()}; " +
                "chmod 777 \"\$p\" 2>/dev/null || true; " +
                "if [ -e \"\$p\" ]; then " +
                "mv -f \"\$p\" \"\$p.stale.\$\$\" 2>/dev/null || true; " +
                "rm -f \"\$p\" \"\$p.stale.\"* 2>/dev/null || true; " +
                "fi",
        )
    }

    /**
     * App 可读自身 base.apk，Shell/Shizuku 往往读不了 /data/app。
     * 流程：App 落到 cache 暂存（0777）→ 再拷到 daemon 工作区（0777）。
     */
    private suspend fun copyServerApkToWorkDir(sourcePath: String, destPath: String): Boolean {
        val source = File(sourcePath)
        val dest = File(destPath)
        runCatching { dest.parentFile?.mkdirs() }
        forceChmod777(dest.parent ?: pathsDirOf(destPath), destPath)

        val staging = File(application.cacheDir, "tweak_server_apk_staging.apk")
        val staged = runCatching {
            staging.parentFile?.mkdirs()
            source.inputStream().use { input ->
                FileOutputStream(staging).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            staging.setReadable(true, false)
            staging.setWritable(true, false)
            forceChmod777(staging.absolutePath)
            staging.isFile && staging.length() == source.length() && staging.length() > 0L
        }.onFailure {
            logE("stage server.apk to cache failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
        if (!staged) {
            logE("cannot stage base.apk into app cache", null, TAG)
            return false
        }
        logD("server.apk staged cache len=${staging.length()}", TAG)

        // 1) 本地直接写入工作区
        val localOk = runCatching {
            forceChmod777(destPath)
            staging.inputStream().use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            forceChmod777(destPath)
            dest.isFile && dest.length() == staging.length()
        }.getOrDefault(false)
        if (localOk) {
            logD("server.apk copied via local stream len=${dest.length()}", TAG)
            return true
        }

        // 2) 特权 Files.copy（从可读的 cache）
        val filesCopyOk = runCatching {
            when (val r = Files.copy(staging.absolutePath, destPath, overwrite = true)) {
                is NativeFileResult.Success -> true
                is NativeFileResult.Failure -> {
                    logD("Files.copy server.apk failed: ${r.error.message}", TAG)
                    false
                }
            }
        }.getOrDefault(false)
        if (filesCopyOk) {
            forceChmod777(destPath)
            val len = measureFileLength(destPath)
            if (len > 0L && len == staging.length()) {
                logD("server.apk copied via Files.copy len=$len", TAG)
                return true
            }
        }

        // 3) 特权 FD
        val fdOk = runCatching {
            val backend = resolvePrivilegedBackendCached()
            forceChmod777(destPath)
            staging.inputStream().use { input ->
                openPrivilegedWriteOnlyFd(
                    backend = backend,
                    path = destPath,
                    create = true,
                    truncate = true,
                ).use { writePfd ->
                    ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            }
            forceChmod777(destPath)
            measureFileLength(destPath) == staging.length()
        }.onFailure {
            logE("privileged fd copy server.apk failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
        if (fdOk) {
            logD("server.apk copied via privileged fd len=${measureFileLength(destPath)}", TAG)
            return true
        }

        // 4) shell：从 world-readable cache cp（不要直接 cp /data/app）
        val shellOut = ReusableShells.execSync(
            "dest=${destPath.shellQuote()}; " +
                "src=${staging.absolutePath.shellQuote()}; " +
                "chmod 777 \"\$(dirname \"\$dest\")\" 2>/dev/null || true; " +
                "chmod 777 \"\$dest\" 2>/dev/null || true; " +
                "rm -f \"\$dest\" 2>/dev/null || true; " +
                "cp \"\$src\" \"\$dest\" && chmod 777 \"\$dest\" && " +
                "(stat -c '%s' \"\$dest\" 2>/dev/null || wc -c < \"\$dest\")",
        ).trim().lines().lastOrNull()?.trim().orEmpty()
        val shellLen = shellOut.toLongOrNull() ?: -1L
        if (shellLen == staging.length() && shellLen > 0L) {
            logD("server.apk copied via shell cp len=$shellLen", TAG)
            return true
        }
        logE(
            "all server.apk copy paths failed shellLen=$shellLen sourceLen=${staging.length()} out=$shellOut",
            null,
            TAG,
        )
        return false
    }

    private fun pathsDirOf(filePath: String): String =
        filePath.substringBeforeLast('/', missingDelimiterValue = filePath)

    private suspend fun measureFileLength(path: String): Long {
        val local = File(path)
        if (local.isFile) {
            val len = local.length()
            if (len > 0L) return len
        }
        Files.length(path).getOrNull()?.takeIf { it > 0L }?.let { return it }
        val shell = ReusableShells.execSync(
            "stat -c '%s' ${path.shellQuote()} 2>/dev/null || wc -c < ${path.shellQuote()}",
        ).trim().lines().lastOrNull()?.trim().orEmpty()
        return shell.toLongOrNull() ?: -1L
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
        if (standaloneServerAlive(paths)) {
            return "mode=skip_already_running pidof=tweak_server"
        }

        val pkgName = application.packageName
        val starter = paths.starterBin
        val serverApk = resolveActiveServerApkPath(paths)
        val bootLog = "${paths.dir}/starter.boot.log"

        require(File(serverApk).isFile || Files.exists(serverApk).getOrNull() == true) {
            "server.apk missing at $serverApk, install() should have prepared it"
        }
        forceChmod777(serverApk)

        ReusableShells.execSync(
            "chmod 755 ${starter.shellQuote()} 2>/dev/null; " +
                "rm -f ${bootLog.shellQuote()} 2>/dev/null || true",
        )

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
        if (waitUntil({ pingInternal() && standaloneServerAlive(paths) }, attempts = 40, delayMs = 25)) {
            return "mode=standalone_bg\n$bg"
        }
        if (standaloneServerAlive(paths)) {
            val binderOk = waitUntil({ pingInternal() }, attempts = 24, delayMs = 25)
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
        if (waitUntil({ pingInternal() && standaloneServerAlive(paths) }, attempts = 32, delayMs = 25)) {
            return "mode=exec_detached\n$viaService\n$bg"
        }

        // 3) 最后回退嵌入（强停可能一起没）
        val embedded = runCatching {
            when (val r = Files.startTweakServerEmbedded(pkgName)) {
                is NativeFileResult.Success -> "embedded=ok"
                is NativeFileResult.Failure -> "embedded=fail:${r.error.message}"
            }
        }.getOrElse { "embedded=error:${it.message}" }
        if (waitUntil({ pingInternal() }, attempts = 20, delayMs = 25)) {
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

    /** 独立 tweak_server 是否存活（先读 pidfile，必要时再 pidof） */
    private suspend fun standaloneServerAlive(paths: DaemonPaths.Resolved): Boolean {
        val pid = readServerPid(paths)
        if (pid > 0 && !isFileServicePid(pid)) {
            if (File("/proc/$pid").exists()) {
                return true
            }
            if (Files.exists("/proc/$pid").getOrNull() == true) {
                return true
            }
        }
        return shellServerAlive()
    }

    private suspend fun waitForBinderAttach(attempts: Int, delayMs: Long): Boolean =
        waitUntil({ pingInternal() }, attempts, delayMs)

    private suspend fun isRunningInternal(paths: DaemonPaths.Resolved): Boolean =
        standaloneServerAlive(paths)

    private suspend fun readServerPid(paths: DaemonPaths.Resolved): Int {
        val local = File(paths.serverPid)
        val text = when {
            local.isFile && local.canRead() -> runCatching { local.readText() }.getOrNull()
            else -> Files.readText(paths.serverPid).getOrNull()
        }
            ?.trim()
            ?.lines()
            ?.firstOrNull()
            .orEmpty()
        return text.toIntOrNull() ?: -1
    }

    private suspend fun resolvePrivilegedBackendCached(): NativeFileBackend {
        cachedPrivilegedBackend?.let { return it }
        return resolvePrivilegedBackend().also { cachedPrivilegedBackend = it }
    }

    private suspend fun resolvePrivilegedBackend(): NativeFileBackend {
        val runtimeMode = GlobalViewModel.runtimeModeState.value
            ?: withTimeoutOrNull(5.seconds) {
                GlobalViewModel.runtimeModeState.filterNotNull().first()
            }
            ?: TweakDataStore.runtimeModeFlow().first()
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
        if (condition()) return true
        repeat(attempts) {
            delay(delayMs.milliseconds)
            if (condition()) return true
        }
        return false
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
}
