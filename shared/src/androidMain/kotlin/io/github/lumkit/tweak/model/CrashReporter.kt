package io.github.lumkit.tweak.model

import android.content.Intent
import android.os.Process
import io.github.lumkit.tweak.MainActivity
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.crash.CrashLogSource
import io.github.lumkit.tweak.common.crash.CrashLogStore
import io.github.lumkit.tweak.common.crash.CrashWriteResult
import io.github.lumkit.tweak.common.utils.BRAND
import io.github.lumkit.tweak.common.utils.BUILD_VERSION_CODE
import io.github.lumkit.tweak.common.utils.BUILD_VERSION_NAME
import io.github.lumkit.tweak.common.utils.MODEL
import io.github.lumkit.tweak.common.utils.SDK_INT
import io.github.lumkit.tweak.common.utils.SDK_RELEASE
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.packageName
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

object CrashReporter {
    private const val TAG = "CrashReporter"
    /** 避免超出 Binder Intent 体积上限。 */
    private const val MAX_REPORT_CHARS = 400_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var installed = false
    private var handling = false

    fun install() {
        if (installed) {
            return
        }
        installed = true
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable, defaultHandler)
        }
    }

    fun absorbCrashReportFromIntent(intent: Intent?): Boolean {
        val reportJson = intent
            ?.getStringExtra(ConstCommon.Navigation.EXTRA_CRASH_REPORT)
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return runCatching {
            val report = json.decodeFromString<CrashReport>(reportJson)
            CrashSession.setPending(report)
            true
        }.getOrElse {
            logE("absorb crash report failed: ${it.stackTraceToString()}", tag = TAG)
            false
        }
    }

    fun clearPending() {
        CrashSession.clear()
    }

    private fun handleUncaughtException(
        thread: Thread,
        throwable: Throwable,
        defaultHandler: Thread.UncaughtExceptionHandler?,
    ) {
        if (handling) {
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }
        handling = true
        runCatching {
            val report = buildReport(thread, throwable)
            when (val written = CrashLogStore.write(CrashLogSource.CLIENT, thread.name, throwable)) {
                is CrashWriteResult.Failed -> logE("client crash log was not stored: ${written.reason}", tag = TAG)
                is CrashWriteResult.Stored -> Unit
            }
            CrashSession.setPending(report)
            relaunchToCrashScreen(report)
        }.onFailure {
            logE("crash handler failed: ${it.stackTraceToString()}", tag = TAG)
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    private fun buildReport(thread: Thread, throwable: Throwable): CrashReport {
        val root = generateSequence(throwable) { it.cause }.last()
        return CrashReport(
            timestamp = System.currentTimeMillis(),
            threadName = thread.name,
            exceptionName = root::class.qualifiedName ?: root::class.simpleName.orEmpty(),
            message = root.message.orEmpty(),
            stackTrace = throwable.stackTraceToString(),
            packageName = packageName,
            versionName = BUILD_VERSION_NAME,
            versionCode = BUILD_VERSION_CODE,
            brand = BRAND,
            model = MODEL,
            sdkInt = SDK_INT,
            sdkRelease = SDK_RELEASE,
        )
    }

    private fun encodeForIntent(report: CrashReport): String {
        var payload = report
        var encoded = json.encodeToString(payload)
        if (encoded.length > MAX_REPORT_CHARS) {
            val keep = (MAX_REPORT_CHARS / 2).coerceAtLeast(1)
            payload = report.copy(
                stackTrace = report.stackTrace.take(keep) + "\n...(truncated)",
            )
            encoded = json.encodeToString(payload)
        }
        return encoded
    }

    private fun relaunchToCrashScreen(report: CrashReport) {
        val intent = Intent(application, MainActivity::class.java).apply {
            action = ConstCommon.Navigation.ACTION_DEEPLINK_SELF
            putExtra(ConstCommon.Navigation.EXTRA_CRASH_REPORT, encodeForIntent(report))
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
        application.startActivity(intent)
    }
}
