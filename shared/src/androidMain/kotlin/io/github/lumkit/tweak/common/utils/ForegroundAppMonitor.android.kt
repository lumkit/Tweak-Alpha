package io.github.lumkit.tweak.common.utils

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.server.battery.ForegroundPackageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ForegroundAppMonitor"
private val started = AtomicBoolean(false)
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val refreshMutex = Mutex()
private var refreshJob: Job? = null
private var uidImportanceListener: Any? = null
private var lifecycleReceiver: BroadcastReceiver? = null
private var fallbackJob: Job? = null

internal actual fun startForegroundAppMonitor() {
    if (!started.compareAndSet(false, true)) {
        scheduleRefresh()
        return
    }
    scope.launch {
        grantUsageStatsOp()
        registerUidImportanceListener()
        registerLifecycleReceiver()
        startFallbackTickerIfNeeded()
        ForegroundAppMonitor._isRunning.value = true
        refreshForegroundPackage()
        logD("foreground monitor started", TAG)
    }
}

internal actual suspend fun refreshForegroundAppMonitor() {
    startForegroundAppMonitor()
    refreshForegroundPackage()
}

private fun scheduleRefresh() {
    refreshJob?.cancel()
    refreshJob = scope.launch {
        delay(80.milliseconds)
        refreshForegroundPackage()
    }
}

private suspend fun grantUsageStatsOp() {
    val pkg = application.packageName
    runCatching {
        ReusableShells.execSync("cmd appops set $pkg GET_USAGE_STATS allow")
    }.onFailure {
        logE("grant GET_USAGE_STATS failed: ${it.message}", it, TAG)
    }
}

private fun registerUidImportanceListener() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    if (uidImportanceListener != null) return
    val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    runCatching {
        val listenerClass = Class.forName("android.app.ActivityManager\$OnUidImportanceListener")
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { _, method, _ ->
            if (method.name == "onUidImportance") {
                scheduleRefresh()
            }
            null
        }
        am.javaClass.getMethod(
            "addOnUidImportanceListener",
            listenerClass,
            Int::class.javaPrimitiveType,
        ).invoke(
            am,
            listener,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
        )
        uidImportanceListener = listener
        logD("uid importance listener registered", TAG)
    }.onFailure {
        logE("register uid importance listener failed: ${it.message}", it, TAG)
    }
}

private fun registerLifecycleReceiver() {
    if (lifecycleReceiver != null) return
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scheduleRefresh()
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_USER_PRESENT)
    }
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(receiver, filter)
        }
        lifecycleReceiver = receiver
    }.onFailure {
        logE("register lifecycle receiver failed: ${it.message}", it, TAG)
    }
}

private fun startFallbackTickerIfNeeded() {
    if (uidImportanceListener != null) return
    if (fallbackJob?.isActive == true) return
    fallbackJob = scope.launch {
        logD("uid importance unavailable, fallback ticker", TAG)
        while (true) {
            delay(1_000.milliseconds)
            refreshForegroundPackage()
        }
    }
}

private suspend fun refreshForegroundPackage() = refreshMutex.withLock {
    val dump = runCatching { dumpWindowFocus() }.getOrDefault("")
    val pkg = ForegroundPackageReader.parseFocusPackage(dump)
        .trim()
        .takeIf { it.isNotEmpty() }
    if (pkg != ForegroundAppMonitor._foregroundPackage.value) {
        ForegroundAppMonitor._foregroundPackage.value = pkg
        logD("foreground: $pkg", TAG)
    }
}

private suspend fun dumpWindowFocus(): String {
    val pattern = ForegroundPackageReader.FOCUS_DUMP_KEYWORDS.joinToString("|")
    return ReusableShells.execSync("""dumpsys window | grep -E "$pattern"""")
}
