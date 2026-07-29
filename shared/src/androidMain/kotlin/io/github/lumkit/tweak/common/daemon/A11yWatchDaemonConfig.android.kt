package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

actual object A11yWatchDaemonConfig {

    private const val TAG = "A11yWatchDaemonConfig"

    actual suspend fun write(
        intervalMs: Int,
        enabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        val paths = DaemonPaths.resolve()
        val safeInterval = intervalMs.coerceAtLeast(DaemonPaths.MIN_A11Y_INTERVAL_MS)
        val component = "${application.packageName}/${ConstCommon.Accessibility.SERVICE_CLASS}"
        val text = buildString {
            append("enabled=").append(if (enabled) "1" else "0").append('\n')
            append("interval_ms=").append(safeInterval).append('\n')
            append("component=").append(component).append('\n')
        }
        try {
            DaemonConfIo.write(
                path = paths.a11yConf,
                text = text,
                workRoot = paths.workRoot,
                dir = paths.dir,
                dirMode = paths.dirMode,
                isRootPrivate = paths.isRootPrivate,
            )
            logD(
                "wrote a11y conf intervalMs=$safeInterval enabled=$enabled path=${paths.a11yConf}",
                TAG,
            )
        } catch (e: Exception) {
            logE("write a11y conf failed: ${e.message}", e, TAG)
        }
    }

    actual suspend fun syncFromDataStore() {
        val intervalMs = TweakDataStore.a11yWatchIntervalMsFlow().first()
        val enabled = TweakDataStore.a11yDaemonEnabledFlow().first()
        write(intervalMs = intervalMs, enabled = enabled)
    }
}
