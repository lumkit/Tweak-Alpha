package io.github.lumkit.tweak.common.daemon

import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

actual object BatteryRecordDaemonConfig {

    private const val TAG = "BatteryRecordDaemonConfig"

    actual suspend fun write(
        intervalMs: Int,
        maxPartBytes: Long,
        enabled: Boolean,
        dualCell: Boolean,
        currentScale: Long,
    ) = withContext(Dispatchers.IO) {
        val paths = DaemonPaths.resolve()
        val safeInterval = intervalMs.coerceAtLeast(100)
        val safeMax = maxPartBytes.coerceAtLeast(64 * 1024L)
        val scale = BatteryReadingNormalize.coerceScale(currentScale)
        val text = buildString {
            append("enabled=").append(if (enabled) "1" else "0").append('\n')
            append("interval_ms=").append(safeInterval).append('\n')
            append("max_part_bytes=").append(safeMax).append('\n')
            append("dual_cell=").append(if (dualCell) "1" else "0").append('\n')
            append("current_scale=").append(scale).append('\n')
        }
        try {
            DaemonConfIo.write(
                path = paths.batteryRecordConf,
                text = text,
                workRoot = paths.workRoot,
                dir = paths.dir,
                dirMode = paths.dirMode,
                isRootPrivate = paths.isRootPrivate,
            )
            logD(
                "wrote battery conf intervalMs=$safeInterval maxPart=$safeMax enabled=$enabled " +
                    "dualCell=$dualCell scale=$scale path=${paths.batteryRecordConf}",
                TAG,
            )
        } catch (e: Exception) {
            logE("write battery conf failed: ${e.message}", e, TAG)
        }
    }

    actual suspend fun syncFromDataStore() {
        val nativeOn = TweakDataStore.nativeDaemonEnabledFlow().first()
        val intervalMs = TweakDataStore.batteryRecordSampleIntervalMsFlow().first()
        val dualCell = TweakDataStore.batteryDualCellFlow().first()
        val currentScale = TweakDataStore.batteryCurrentScaleFlow().first()
        write(
            intervalMs = intervalMs,
            maxPartBytes = DaemonPaths.DEFAULT_BATTERY_LOG_MAX_PART_BYTES,
            enabled = nativeOn,
            dualCell = dualCell,
            currentScale = currentScale,
        )
    }
}
