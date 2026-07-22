package io.github.lumkit.tweak.common.daemon

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "TweakDaemon"
private const val NATIVE_LIB_NAME = "libtweakd.so"

actual object TweakDaemon {

    actual suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val src = resolvePackagedBinary()
                ?: error("packaged $NATIVE_LIB_NAME not found")
            val bin = ConstCommon.Path.DAEMON_BIN
            val dir = ConstCommon.Path.DAEMON_DIR
            ReusableShells.execSync(
                """
                mkdir -p ${dir.shellQuote()}
                cp -f ${src.absolutePath.shellQuote()} ${bin.shellQuote()}
                chmod 755 ${bin.shellQuote()}
                """.trimIndent().replace('\n', ';'),
            )
            shellFileExists(bin)
        }.onFailure {
            logE("install failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (isRunningInternal()) {
                return@runCatching true
            }
            if (!install()) {
                return@runCatching false
            }
            val bin = ConstCommon.Path.DAEMON_BIN
            val pid = ConstCommon.Path.DAEMON_PID
            val sock = ConstCommon.Path.DAEMON_SOCK
            val log = ConstCommon.Path.DAEMON_LOG
            ReusableShells.execSync(
                """
                mkdir -p ${ConstCommon.Path.DAEMON_DIR.shellQuote()}
                setsid ${bin.shellQuote()} --daemon --pid ${pid.shellQuote()} --sock ${sock.shellQuote()} \
                  >>${log.shellQuote()} 2>&1 < /dev/null &
                """.trimIndent().lines().joinToString(" ") { it.trim() },
            )
            waitUntil({ isRunningInternal() || pingInternal() }, attempts = 20, delayMs = 100)
        }.onFailure {
            logE("start failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!isRunningInternal() && !sockExists()) {
                return@runCatching true
            }
            val stoppedByCmd = runCatching {
                request("STOP").startsWith("OK")
            }.getOrDefault(false)
            if (!stoppedByCmd) {
                val pid = readPid()
                if (pid > 0) {
                    ReusableShells.execSync("kill -TERM $pid 2>/dev/null; kill -KILL $pid 2>/dev/null")
                }
            }
            waitUntil({ !isRunningInternal() }, attempts = 20, delayMs = 100)
            ReusableShells.execSync(
                "rm -f ${ConstCommon.Path.DAEMON_SOCK.shellQuote()} ${ConstCommon.Path.DAEMON_PID.shellQuote()}",
            )
            true
        }.onFailure {
            logE("stop failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    actual suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        isRunningInternal()
    }

    actual suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        pingInternal()
    }

    actual suspend fun status(): TweakDaemonStatus? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = request("STATUS").trim()
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
                sock = map["sock"].orEmpty(),
                raw = raw,
            )
        }.onFailure {
            logD("status failed: ${it.message}", TAG)
        }.getOrNull()
    }

    actual suspend fun version(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = request("VERSION").trim()
            if (raw.startsWith("OK")) raw.removePrefix("OK").trim() else null
        }.getOrNull()
    }

    private suspend fun isRunningInternal(): Boolean {
        val pid = readPid()
        if (pid <= 0) {
            return false
        }
        return shellFileExists("/proc/$pid")
    }

    private suspend fun pingInternal(): Boolean =
        runCatching { request("PING").trim() == "PONG" }.getOrDefault(false)

    private fun resolvePackagedBinary(): File? {
        val nativeDir = application.applicationInfo.nativeLibraryDir?.let(::File)
        if (nativeDir != null) {
            val direct = File(nativeDir, NATIVE_LIB_NAME)
            if (direct.exists()) {
                return direct
            }
        }
        Build.SUPPORTED_ABIS.orEmpty().forEach { abi ->
            val candidate = File(nativeDir, "$abi/$NATIVE_LIB_NAME")
            if (candidate.exists()) {
                return candidate
            }
        }
        return null
    }

    private suspend fun readPid(): Int {
        val viaShell = ReusableShells.execSync(
            "cat ${ConstCommon.Path.DAEMON_PID.shellQuote()} 2>/dev/null",
        ).trim().lines().firstOrNull().orEmpty()
        viaShell.toIntOrNull()?.let { return it }
        return runCatching {
            File(ConstCommon.Path.DAEMON_PID).readText().trim().toInt()
        }.getOrDefault(-1)
    }

    private suspend fun sockExists(): Boolean {
        return File(ConstCommon.Path.DAEMON_SOCK).exists() ||
            shellFileExists(ConstCommon.Path.DAEMON_SOCK)
    }

    private suspend fun shellFileExists(path: String): Boolean {
        val out = ReusableShells.execSync(
            "if [ -e ${path.shellQuote()} ]; then echo 1; else echo 0; fi",
        ).trim()
        return out.endsWith("1")
    }

    private suspend fun request(command: String): String {
        val sock = ConstCommon.Path.DAEMON_SOCK
        runCatching {
            return localSocketRequest(sock, command)
        }.onFailure {
            logD("LocalSocket failed, fallback shell: ${it.message}", TAG)
        }
        return ReusableShells.execSync(
            "printf '%s\\n' ${command.shellQuote()} | timeout 2s nc -U ${sock.shellQuote()} 2>/dev/null || " +
                "printf '%s\\n' ${command.shellQuote()} | timeout 2s busybox nc -U ${sock.shellQuote()} 2>/dev/null",
        ).trim()
    }

    private fun localSocketRequest(sockPath: String, command: String): String {
        LocalSocket().use { socket ->
            socket.connect(
                LocalSocketAddress(sockPath, LocalSocketAddress.Namespace.FILESYSTEM),
            )
            val writer = OutputStreamWriter(socket.outputStream)
            writer.write(command)
            writer.write("\n")
            writer.flush()
            return BufferedReader(InputStreamReader(socket.inputStream)).readLine().orEmpty()
        }
    }

    private suspend fun waitUntil(
        condition: suspend () -> Boolean,
        attempts: Int,
        delayMs: Long,
    ): Boolean {
        repeat(attempts) {
            if (condition()) {
                return true
            }
            delay(delayMs.milliseconds)
        }
        return condition()
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
}
