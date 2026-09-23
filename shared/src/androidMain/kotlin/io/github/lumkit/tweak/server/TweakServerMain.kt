package io.github.lumkit.tweak.server

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.system.Os
import androidx.annotation.Keep
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.crash.CrashLogSource
import io.github.lumkit.tweak.common.crash.CrashLogStore
import io.github.lumkit.tweak.common.daemon.DaemonPaths
import io.github.lumkit.tweak.common.daemon.DaemonProcessIdentity
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.server.TweakServerMain.main
import io.github.lumkit.tweak.server.TweakServerMain.startEmbedded
import io.github.lumkit.tweak.server.battery.BatteryEngine
import io.github.lumkit.tweak.server.fakecontext.FakeContext
import io.github.lumkit.tweak.server.ipc.BinderDelivery
import io.github.lumkit.tweak.server.ipc.TweakServerBinder
import io.github.lumkit.tweak.sharednative.BatteryBridge
import io.github.lumkit.tweak.sharednative.HostPresenceWatch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * app_process / Shizuku UserService 入口：特权常驻 TweakServer。
 *
 * - [main]：独立 `app_process`（Root / adb shell 启动）
 * - [startEmbedded]：嵌入已常驻的 Shizuku `file_service` 进程（避免嵌套 app_process 秒退）
 */
@Keep
object TweakServerMain {

    private const val TAG = "TweakServerMain"
    const val VERSION = "2.0.2-c3"
    private const val BINDER_REDELIVER_MS = 60_000L
    private const val WATCHDOG_PEER_MS = 5_000L
    private val crashRecorded = AtomicBoolean(false)

    private data class Session(
        val packageName: String,
        val pidFile: File,
        val batteryEngine: BatteryEngine,
        val binder: TweakServerBinder,
        val serverBinder: android.os.IBinder,
        val embedded: Boolean,
        val hostWatch: HostPresenceWatch?,
        val mainHandler: Handler,
        val binderRedeliver: Runnable,
        val watchdogPeer: Runnable,
    )

    @Volatile
    private var session: Session? = null

    @Keep
    @JvmStatic
    fun main(args: Array<String>) {
        runCatching {
            File("/data/local/tmp/tweak-alpha/daemon/java.boot.log")
                .writeText("java_main pid=${Process.myPid()} uid=${Process.myUid()}\n")
        }
        if (Looper.getMainLooper() == null) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()
        }
        val mainHandler = Handler(Looper.getMainLooper())

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordDaemonCrash(thread, error)
            exitProcess(255)
        }
        try {
            bootstrap(
                packageName = resolveAppPackageName(args),
                embedded = false,
                mainHandler = mainHandler,
            )
            Looper.loop()
        } catch (error: Throwable) {
            recordDaemonCrash(Thread.currentThread(), error)
            exitProcess(1)
        }
    }

    /**
     * 在 Shizuku UserService 进程内启动（不阻塞、不 exitProcess）。
     * 由 [io.github.lumkit.tweak.sharednative.FileServiceDelegate] 反射调用。
     */
    @Keep
    @JvmStatic
    @Synchronized
    fun startEmbedded(packageName: String): Boolean {
        return runCatching {
            runCatching {
                File("/data/local/tmp/tweak-alpha/daemon/java.boot.log")
                    .writeText(
                        "java_embedded pid=${Process.myPid()} uid=${Process.myUid()} pkg=$packageName\n",
                    )
            }
            val looper = Looper.getMainLooper()
                ?: error("MainLooper missing in UserService process")
            val mainHandler = Handler(looper)
            bootstrap(
                packageName = packageName.ifBlank { ConstCommon.APP_PACKAGE_DEFAULT },
                embedded = true,
                mainHandler = mainHandler,
            )
            true
        }.onFailure {
            logE("startEmbedded failed: ${it.message}", it, TAG)
        }.getOrDefault(false)
    }

    @Keep
    @JvmStatic
    @Synchronized
    fun stopEmbedded(): Boolean {
        val current = session ?: return true
        if (!current.embedded) return false
        teardown(cleanupWorkspace = false, reason = "stopEmbedded")
        return true
    }

    @Synchronized
    private fun bootstrap(
        packageName: String,
        embedded: Boolean,
        mainHandler: Handler,
    ) {
        session?.let { existing ->
            BinderDelivery.sendToApp(existing.packageName, existing.serverBinder)
            logE(
                "already started embedded=${existing.embedded} pid=${Process.myPid()} redeliver binder",
                null,
                TAG,
            )
            return
        }

        FakeContext.systemContext
        runCatching { BatteryBridge.init(FakeContext.systemContext) }
            .onFailure { logE("BatteryBridge.init failed: ${it.message}", it, TAG) }
        val daemonDir = File(DaemonPaths.WORK_ROOT, "daemon")
        if (!daemonDir.mkdirs() && !daemonDir.isDirectory) {
            error("cannot create daemon dir: ${daemonDir.absolutePath}")
        }
        runCatching { Os.chmod(daemonDir.absolutePath, 511) }
        File(daemonDir, DaemonPaths.BATTERY_LOGS_DIR_NAME).mkdirs()

        val pidFile = File(daemonDir, DaemonPaths.SERVER_PID_NAME)
        if (!embedded && alreadyRunning(pidFile)) {
            logE("already running pidfile=${pidFile.absolutePath}", null, TAG)
            exitProcess(2)
        }
        if (!embedded && alreadyRunningStandalone(pidFile)) {
            logE("already running standalone tweak_server", null, TAG)
            exitProcess(2)
        }
        writePid(pidFile)

        val batteryEngine = BatteryEngine.createDefault(daemonDir)
        batteryEngine.start()

        val holdFile = File(daemonDir, DaemonPaths.KEEPALIVE_HOLD_NAME)
        val hostWatch = if (embedded) {
            null
        } else {
            HostPresenceWatch(
                packageName = packageName,
                context = FakeContext.systemContext,
                mainHandler = mainHandler,
                onHostGone = {
                    runCatching { holdFile.writeText("1\n") }
                    teardown(cleanupWorkspace = true, reason = "host uninstalled")
                },
            )
        }

        val binder = TweakServerBinder(
            version = VERSION,
            packageName = packageName,
            mainHandler = mainHandler,
            statusExtra = { batteryEngine.lastStatusLine },
            onStop = {
                teardown(cleanupWorkspace = false, reason = "binder stop")
            },
            onReload = {
                batteryEngine.reloadConfig()
            },
        )
        val serverBinder = binder.asBinder()
        val sent = BinderDelivery.sendToApp(packageName, serverBinder)
        logE(
            "started embedded=$embedded uid=${Process.myUid()} pid=${Process.myPid()} " +
                "pkg=$packageName binderSent=$sent",
            null,
            TAG,
        )

        val binderRedeliver = object : Runnable {
            override fun run() {
                val current = session ?: return
                BinderDelivery.sendToApp(current.packageName, current.serverBinder)
                current.mainHandler.postDelayed(this, BINDER_REDELIVER_MS)
            }
        }
        val watchdogPeer = object : Runnable {
            override fun run() {
                val current = session ?: return
                if (!current.embedded) {
                    runCatching { ensureWatchdogProcess(current.packageName) }
                }
                current.mainHandler.postDelayed(this, WATCHDOG_PEER_MS)
            }
        }
        session = Session(
            packageName = packageName,
            pidFile = pidFile,
            batteryEngine = batteryEngine,
            binder = binder,
            serverBinder = serverBinder,
            embedded = embedded,
            hostWatch = hostWatch,
            mainHandler = mainHandler,
            binderRedeliver = binderRedeliver,
            watchdogPeer = watchdogPeer,
        )
        runCatching { hostWatch?.start() }
            .onFailure { logE("host watch start failed: ${it.message}", it, TAG) }

        mainHandler.postDelayed({
            val current = session ?: return@postDelayed
            BinderDelivery.sendToApp(current.packageName, current.serverBinder)
        }, 350L)
        mainHandler.postDelayed({
            val current = session ?: return@postDelayed
            BinderDelivery.sendToApp(current.packageName, current.serverBinder)
        }, 1_000L)
        mainHandler.postDelayed(binderRedeliver, 2_000L)
        if (!embedded) {
            preferSurviveLmk()
            mainHandler.postDelayed(watchdogPeer, WATCHDOG_PEER_MS)
        }
    }

    @Synchronized
    private fun teardown(cleanupWorkspace: Boolean, reason: String) {
        val current = session ?: return
        logE(
            "self-stop begin reason=$reason embedded=${current.embedded} " +
                "pid=${Process.myPid()} cleanupWorkspace=$cleanupWorkspace",
            null,
            TAG,
        )
        runCatching { current.hostWatch?.stop() }
        current.mainHandler.removeCallbacks(current.binderRedeliver)
        current.mainHandler.removeCallbacks(current.watchdogPeer)
        current.batteryEngine.stop()
        runCatching { current.pidFile.delete() }
        session = null
        if (cleanupWorkspace) {
            val workRoot = File(DaemonPaths.WORK_ROOT)
            val deleted = runCatching { workRoot.deleteRecursively() }.getOrDefault(false)
            logE("workspace cleanup path=${workRoot.absolutePath} deleted=$deleted", null, TAG)
        }
        if (cleanupWorkspace || !current.embedded) {
            exitProcess(0)
        }
    }

    private fun recordDaemonCrash(thread: Thread, error: Throwable) {
        if (!crashRecorded.compareAndSet(false, true)) return
        logE("uncaught in ${thread.name}: ${error.message}", error, TAG)
        CrashLogStore.write(CrashLogSource.DAEMON, thread.name, error)
    }

    private fun resolveAppPackageName(args: Array<String>): String {
        val fromArg = args.firstOrNull { it.startsWith("--package=") }?.substringAfter("=")
        if (!fromArg.isNullOrBlank()) return fromArg
        return ConstCommon.APP_PACKAGE_DEFAULT
    }

    private fun writePid(file: File) {
        file.parentFile?.mkdirs()
        file.writeText("${Process.myPid()}\n")
        runCatching { Os.chmod(file.absolutePath, 420) }
    }

    private fun alreadyRunning(pidFile: File): Boolean {
        val selfPid = Process.myPid()
        if (!pidFile.isFile) return false
        val pid = pidFile.readText().trim().toIntOrNull() ?: return false
        if (pid <= 0 || pid == selfPid) return false
        // Os.kill(pid, 0) 对任意仍存活的 pid 都会成功，pid 复用后会误判并直接退出。
        return isTweakServerCmdline(pid)
    }

    private fun isTweakServerCmdline(pid: Int): Boolean {
        val file = File("/proc/$pid/cmdline")
        if (!file.isFile || !file.canRead()) return false
        val text = runCatching {
            file.readBytes().toString(Charsets.ISO_8859_1).replace('\u0000', ' ')
        }.getOrNull()
        return DaemonProcessIdentity.isTweakServerCmdline(text)
    }

    private fun preferSurviveLmk() {
        runCatching { File("/proc/self/oom_score_adj").writeText("-1000") }
    }

    private fun ensureWatchdogProcess(packageName: String) {
        val daemonDir = File(DaemonPaths.WORK_ROOT, "daemon")
        if (File(daemonDir, DaemonPaths.KEEPALIVE_HOLD_NAME).exists()) return
        if (processRunning("tweak_watchdog")) return
        val starter = File(daemonDir, DaemonPaths.STARTER_BIN_NAME)
        val apk = File(daemonDir, DaemonPaths.SERVER_APK_NAME)
        if (!starter.isFile || !apk.isFile) return
        Runtime.getRuntime().exec(
            arrayOf(
                starter.absolutePath,
                "--watchdog",
                "--apk=${apk.absolutePath}",
                "--package=$packageName",
            ),
        )
    }

    private fun processRunning(name: String): Boolean {
        val text = runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "grep -l '^$name$' /proc/[0-9]*/comm 2>/dev/null | head -n 1"))
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("")
        if (text.isNotEmpty()) return true
        val pidof = runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "pidof $name 2>/dev/null"))
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("")
        return pidof.split(Regex("\\s+")).any { token ->
            token.toIntOrNull()?.let { it > 0 } == true
        }
    }

    /** pidfile 丢失时，避免再拉起第二个 tweak_server */
    private fun alreadyRunningStandalone(pidFile: File): Boolean {
        val selfPid = Process.myPid()
        val pidText = runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "pidof tweak_server 2>/dev/null"))
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("")
        val pid = pidText.split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: return false
        if (pid <= 0 || pid == selfPid) return false
        runCatching { pidFile.writeText("$pid\n") }
        return true
    }
}
