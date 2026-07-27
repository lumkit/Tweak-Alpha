package io.github.lumkit.tweak.common.daemon

import android.os.Build
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "TweakDaemon"
private const val ASSET_ROOT = "tweakd"
private const val ASSET_BIN_NAME = "tweakd"

actual object TweakDaemon {

    @Volatile
    private var cachedAssetSha256: String? = null

    actual suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val paths = DaemonPaths.resolve()
            val backend = resolvePrivilegedBackend()

            PrivilegedWorkDir.ensureWritable(
                path = paths.dir,
                workRoot = paths.workRoot,
                mode = paths.dirMode,
            )

            val expectedSha = expectedAssetSha256()
                ?: error("assets/$ASSET_ROOT/<abi>/$ASSET_BIN_NAME not found for ${Build.SUPPORTED_ABIS?.joinToString()}")

            if (isInstalledBinaryPresent(paths.bin)) {
                val actualSha = hashInstalledBinary(backend, paths.bin)
                if (actualSha != null && actualSha.equals(expectedSha, ignoreCase = true)) {
                    Files.chmod(paths.bin, "0755")
                    logD(
                        "tweakd already installed and intact, skip copy sha256=$actualSha dir=${paths.dir}",
                        TAG,
                    )
                    return@runCatching true
                }
                logD(
                    "tweakd corrupt or outdated actual=${actualSha ?: "unreadable"}, reinstall",
                    TAG,
                )
                runCatching { Files.delete(paths.bin) }
            } else {
                logD("tweakd not installed at ${paths.bin}, copy from assets", TAG)
            }

            val (assetPath, input) = openPackagedBinaryAsset()
                ?: error("packaged asset missing during copy")
            input.use { stream ->
                copyAssetToPrivilegedPath(backend, stream, paths.bin)
            }
            Files.chmod(paths.bin, "0755").throwIfFailed("chmod ${paths.bin}")

            val len = Files.length(paths.bin).getOrNull() ?: 0L
            if (len <= 0L) {
                error("installed binary empty: ${paths.bin}")
            }
            val afterSha = hashInstalledBinary(backend, paths.bin)
            if (afterSha == null || !afterSha.equals(expectedSha, ignoreCase = true)) {
                runCatching { Files.delete(paths.bin) }
                error("installed binary integrity failed expected=$expectedSha actual=$afterSha")
            }
            logD("installed tweakd -> ${paths.bin} len=$len asset=$assetPath sha256=$afterSha", TAG)
            true
        }.onFailure {
            logE("install failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!install()) {
                return@runCatching false
            }
            val paths = DaemonPaths.resolve()
            if (isRunningInternal(paths) && pingInternal(paths)) {
                return@runCatching true
            }
            Files.delete(paths.port)
            Files.delete(paths.pid)

            val launchOut = launchDaemonProcess(paths)
            logD("launch output:\n$launchOut", TAG)

            val started = waitUntil(
                { isRunningInternal(paths) || pingInternal(paths) },
                attempts = 40,
                delayMs = 100,
            )
            if (!started) {
                logStartFailure(paths, launchOut)
            }
            started
        }.onFailure {
            logE("start failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val paths = DaemonPaths.resolve()
            if (!isRunningInternal(paths) && !portExists(paths)) {
                return@runCatching true
            }
            val stoppedByCmd = runCatching {
                request(paths, "STOP").startsWith("OK")
            }.getOrDefault(false)
            if (!stoppedByCmd) {
                val pid = readPid(paths)
                if (pid > 0) {
                    ReusableShells.execSync("kill -TERM $pid 2>/dev/null; kill -KILL $pid 2>/dev/null")
                }
            }
            waitUntil({ !isRunningInternal(paths) }, attempts = 20, delayMs = 100)
            Files.delete(paths.port)
            Files.delete(paths.pid)
            true
        }.onFailure {
            logE("stop failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        isRunningInternal(DaemonPaths.resolve())
    }

    actual suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        pingInternal(DaemonPaths.resolve())
    }

    actual suspend fun status(): TweakDaemonStatus? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = request(DaemonPaths.resolve(), "STATUS").trim()
            if (!raw.startsWith("OK")) {
                return@runCatching null
            }
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
                sock = map["tcp"] ?: map["portfile"] ?: map["sock"].orEmpty(),
                raw = raw,
            )
        }.onFailure {
            logD("status failed: ${it.message}", TAG)
        }.getOrNull()
    }

    actual suspend fun version(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = request(DaemonPaths.resolve(), "VERSION").trim()
            if (raw.startsWith("OK")) raw.removePrefix("OK").trim() else null
        }.getOrNull()
    }

    private suspend fun isInstalledBinaryPresent(binPath: String): Boolean {
        if (Files.exists(binPath).getOrNull() != true) return false
        return (Files.length(binPath).getOrNull() ?: 0L) > 0L
    }

    private suspend fun expectedAssetSha256(): String? {
        cachedAssetSha256?.let { return it }
        val sha = runCatching {
            val (_, input) = openPackagedBinaryAsset() ?: return null
            input.use { sha256Hex(it) }
        }.onFailure {
            logE("hash asset failed: ${it.message}", it, TAG)
        }.getOrNull() ?: return null
        cachedAssetSha256 = sha
        return sha
    }

    private suspend fun hashInstalledBinary(
        backend: NativeFileBackend,
        binPath: String,
    ): String? = runCatching {
        openPrivilegedReadOnlyFd(backend, binPath).use { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { sha256Hex(it) }
        }
    }.onFailure {
        logE("hash installed binary failed: ${it.message}", it, TAG)
    }.getOrNull()

    private fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) continue
            digest.update(buf, 0, n)
        }
        return digest.digest().joinToString(separator = "") { b -> "%02x".format(b) }
    }

    private suspend fun launchDaemonProcess(paths: DaemonPaths.Resolved): String {
        val args = "--daemon --pid ${paths.pid.shellQuote()} --sock ${paths.port.shellQuote()}"
        ReusableShells.execSync("chmod 755 ${paths.bin.shellQuote()} 2>/dev/null || true")

        val direct = ReusableShells.execSync(
            "${paths.bin.shellQuote()} $args 2>&1; echo __TWEAKD_EXIT:\$?",
        )
        if (waitUntil({ isRunningInternal(paths) || pingInternal(paths) }, attempts = 10, delayMs = 50)) {
            return "mode=direct\n$direct"
        }

        val linker = when {
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() -> "/system/bin/linker64"
            else -> "/system/bin/linker"
        }
        val viaLinker = ReusableShells.execSync(
            "if [ -x ${linker.shellQuote()} ]; then " +
                "${linker.shellQuote()} ${paths.bin.shellQuote()} $args 2>&1; echo __TWEAKD_EXIT:\$?; " +
                "else echo __TWEAKD_NO_LINKER:$linker; fi",
        )
        return "mode=direct_then_linker\ndirect:\n$direct\nlinker:\n$viaLinker"
    }

    private suspend fun logStartFailure(paths: DaemonPaths.Resolved, launchOut: String) {
        val daemonLog = Files.readText(paths.log).getOrNull().orEmpty()
        val binLen = Files.length(paths.bin).getOrNull()
        val pidExists = Files.exists(paths.pid).getOrNull()
        val portExists = Files.exists(paths.port).getOrNull()
        val diag = runCatching {
            ReusableShells.execSync(
                "id; ls -l ${paths.bin.shellQuote()} ${paths.pid.shellQuote()} ${paths.port.shellQuote()} 2>&1; " +
                    "head -c 4 ${paths.bin.shellQuote()} | od -An -tx1 2>&1",
            )
        }.getOrDefault("diag failed")
        logE(
            "start timeout; binLen=$binLen pidExists=$pidExists portExists=$portExists dir=${paths.dir}\n" +
                "launchOut=\n$launchOut\n" +
                "diag=\n$diag\n" +
                "logTail=\n${daemonLog.takeLast(2000)}",
            null,
            TAG,
        )
    }

    private suspend fun isRunningInternal(paths: DaemonPaths.Resolved): Boolean {
        val pid = readPid(paths)
        if (pid <= 0) return false
        return Files.exists("/proc/$pid").getOrNull() == true
    }

    private suspend fun pingInternal(paths: DaemonPaths.Resolved): Boolean =
        runCatching { request(paths, "PING").trim() == "PONG" }.getOrDefault(false)

    private suspend fun resolvePrivilegedBackend(): NativeFileBackend {
        val runtimeMode = GlobalViewModel.runtimeModeState.filterNotNull().first()
        val backend = runtimeMode.asNativeFileBackend()
        require(backend != NativeFileBackend.User) {
            "当前运行模式不支持通过特权 Binder 安装 tweakd"
        }
        return backend
    }

    private fun openPackagedBinaryAsset(): Pair<String, InputStream>? {
        val am = application.assets
        for (abi in Build.SUPPORTED_ABIS.orEmpty()) {
            if (abi.isBlank()) continue
            val assetPath = "$ASSET_ROOT/$abi/$ASSET_BIN_NAME"
            val stream = runCatching { am.open(assetPath) }.getOrNull() ?: continue
            logD("found asset: $assetPath", TAG)
            return assetPath to stream
        }
        val listed = runCatching {
            am.list(ASSET_ROOT)?.joinToString().orEmpty()
        }.getOrDefault("")
        logE(
            "no tweakd asset for abis=${Build.SUPPORTED_ABIS?.joinToString()} under $ASSET_ROOT/[$listed]",
            null,
            TAG,
        )
        return null
    }

    private suspend fun copyAssetToPrivilegedPath(
        backend: NativeFileBackend,
        input: InputStream,
        destPath: String,
    ) {
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

    private suspend fun readPid(paths: DaemonPaths.Resolved): Int {
        val text = Files.readText(paths.pid).getOrNull()
            ?.trim()
            ?.lines()
            ?.firstOrNull()
            .orEmpty()
        return text.toIntOrNull() ?: -1
    }

    private suspend fun portExists(paths: DaemonPaths.Resolved): Boolean =
        Files.exists(paths.port).getOrNull() == true

    private suspend fun readDaemonPort(paths: DaemonPaths.Resolved): Int {
        val text = Files.readText(paths.port).getOrNull()
            ?.trim()
            ?.lines()
            ?.firstOrNull()
            .orEmpty()
        return text.toIntOrNull() ?: -1
    }

    private suspend fun request(paths: DaemonPaths.Resolved, command: String): String {
        val port = readDaemonPort(paths)
        if (port in 1..65535) {
            runCatching {
                return tcpRequest(port, command)
            }.onFailure {
                logD("TCP request failed, fallback shell: ${it.message}", TAG)
            }
            return ReusableShells.execSync(
                "printf '%s\\n' ${command.shellQuote()} | timeout 2s nc 127.0.0.1 $port 2>/dev/null || " +
                    "printf '%s\\n' ${command.shellQuote()} | timeout 2s busybox nc 127.0.0.1 $port 2>/dev/null",
            ).trim()
        }
        return ""
    }

    private fun tcpRequest(port: Int, command: String): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
            socket.soTimeout = 2_000
            val writer = OutputStreamWriter(socket.getOutputStream())
            writer.write(command)
            writer.write("\n")
            writer.flush()
            return BufferedReader(InputStreamReader(socket.getInputStream())).readLine().orEmpty()
        }
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

    private fun NativeFileResult<*>.throwIfFailed(op: String) {
        if (this is NativeFileResult.Failure) {
            error("$op failed: ${error.message}")
        }
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
}
