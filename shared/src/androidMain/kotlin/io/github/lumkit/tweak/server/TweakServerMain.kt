package io.github.lumkit.tweak.server

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.system.Os
import androidx.annotation.Keep
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.daemon.DaemonPaths
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.server.a11y.A11yWatchEngine
import io.github.lumkit.tweak.server.battery.BatteryEngine
import io.github.lumkit.tweak.server.fakecontext.FakeContext
import io.github.lumkit.tweak.server.ipc.BinderDelivery
import io.github.lumkit.tweak.server.ipc.TweakServerBinder
import java.io.File
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
    const val VERSION = "2.0.0-c2"
    private const val BINDER_REDELIVER_MS = 60_000L

    private data class Session(
        val packageName: String,
        val pidFile: File,
        val batteryEngine: BatteryEngine,
        val a11yEngine: A11yWatchEngine,
        val binder: TweakServerBinder,
        val serverBinder: android.os.IBinder,
        val embedded: Boolean,
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

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logE("uncaught in ${t.name}: ${e.message}", e, TAG)
            exitProcess(255)
        }
        try {
            bootstrap(
                packageName = resolveAppPackageName(args),
                embedded = false,
                mainHandler = mainHandler,
            )
            Looper.loop()
        } catch (t: Throwable) {
            logE("fatal: ${t.message}", t, TAG)
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
        return runCatching {
            current.batteryEngine.stop()
            current.a11yEngine.stop()
            runCatching { current.pidFile.delete() }
            session = null
            true
        }.getOrDefault(false)
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
        writePid(pidFile)

        val batteryEngine = BatteryEngine.createDefault(daemonDir)
        batteryEngine.start()
        val a11yEngine = A11yWatchEngine.createDefault(daemonDir)
        a11yEngine.start()

        val binder = TweakServerBinder(
            version = VERSION,
            packageName = packageName,
            statusExtra = {
                "${batteryEngine.lastStatusLine} ${a11yEngine.lastStatusLine}"
            },
            onStop = {
                batteryEngine.stop()
                a11yEngine.stop()
                runCatching { pidFile.delete() }
                session = null
                if (!embedded) {
                    exitProcess(0)
                }
            },
            onReload = {
                batteryEngine.reloadConfig()
                a11yEngine.reloadConfig()
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

        session = Session(
            packageName = packageName,
            pidFile = pidFile,
            batteryEngine = batteryEngine,
            a11yEngine = a11yEngine,
            binder = binder,
            serverBinder = serverBinder,
            embedded = embedded,
        )

        var redeliverCount = 0
        val redeliver = object : Runnable {
            override fun run() {
                val current = session ?: return
                BinderDelivery.sendToApp(current.packageName, current.serverBinder)
                redeliverCount++
                val next = if (redeliverCount < 30) 2_000L else BINDER_REDELIVER_MS
                mainHandler.postDelayed(this, next)
            }
        }
        mainHandler.postDelayed(redeliver, 2_000L)
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
        return runCatching {
            Os.kill(pid, 0)
            true
        }.getOrDefault(false)
    }
}
