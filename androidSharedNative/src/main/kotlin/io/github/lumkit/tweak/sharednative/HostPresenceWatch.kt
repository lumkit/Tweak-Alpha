package io.github.lumkit.tweak.sharednative

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log

/**
 * 主程序卸载后回调。更新安装带 [Intent.EXTRA_REPLACING] 会忽略。
 */
class HostPresenceWatch(
    private val packageName: String,
    private val context: Context,
    private val mainHandler: Handler,
    private val onHostGone: () -> Unit,
) {
    private var receiver: BroadcastReceiver? = null
    private var pollRunnable: Runnable? = null
    private var missCount = 0
    @Volatile
    private var stopped = false

    fun start() {
        val poll = object : Runnable {
            override fun run() {
                if (stopped) return
                if (isHostInstalled(context, packageName)) {
                    missCount = 0
                } else {
                    missCount += 1
                    Log.i(TAG, "host missing from install list miss=$missCount pkg=$packageName")
                    if (missCount >= MISS_THRESHOLD) {
                        notifyHostGone("poll")
                        return
                    }
                }
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollRunnable = poll
        mainHandler.postDelayed(poll, POLL_INTERVAL_MS)
        runCatching { registerPackageRemovedReceiver() }
            .onFailure { Log.w(TAG, "register PACKAGE_REMOVED failed: ${it.message}") }
    }

    fun stop() {
        stopped = true
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        val current = receiver ?: return
        receiver = null
        runCatching { context.unregisterReceiver(current) }
    }

    private fun registerPackageRemovedReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        val current = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (stopped) return
                val pkg = intent?.data?.schemeSpecificPart ?: return
                if (pkg != packageName) return
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (replacing) {
                    Log.i(TAG, "host package replacing, ignore pkg=$packageName")
                    return
                }
                Log.i(TAG, "host package removed action=${intent.action} pkg=$packageName")
                mainHandler.postDelayed({
                    if (stopped) return@postDelayed
                    if (!isHostInstalled(context, packageName)) {
                        notifyHostGone("broadcast")
                    }
                }, CONFIRM_DELAY_MS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(current, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(current, filter)
        }
        receiver = current
    }

    private fun notifyHostGone(reason: String) {
        if (stopped) return
        Log.w(TAG, "host gone reason=$reason pkg=$packageName")
        stop()
        onHostGone()
    }

    companion object {
        private const val TAG = "HostPresenceWatch"
        private const val POLL_INTERVAL_MS = 30_000L
        private const val CONFIRM_DELAY_MS = 2_000L
        private const val MISS_THRESHOLD = 2

        fun isHostInstalled(context: Context, packageName: String): Boolean {
            val pm = context.packageManager
            val pmResult = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
                true
            }
            pmResult.getOrNull()?.let { return it }
            val error = pmResult.exceptionOrNull()
            val notFound = error is PackageManager.NameNotFoundException ||
                error?.cause is PackageManager.NameNotFoundException
            if (!notFound) {
                Log.i(TAG, "package query failed, fallback pm path: ${error?.message}")
            }
            return pmPathExists(packageName)
        }

        private fun pmPathExists(packageName: String): Boolean {
            val output = runCatching {
                Runtime.getRuntime()
                    .exec(arrayOf("sh", "-c", "pm path $packageName 2>/dev/null"))
                    .inputStream
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrDefault("")
            return output.contains("package:")
        }
    }
}
