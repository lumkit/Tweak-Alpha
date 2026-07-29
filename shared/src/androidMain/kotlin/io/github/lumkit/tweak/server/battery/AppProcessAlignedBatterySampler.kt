package io.github.lumkit.tweak.server.battery

import android.os.BatteryManager
import io.github.lumkit.tweak.common.utils.BatterySnapshotCore
import io.github.lumkit.tweak.sharednative.BatteryBridge
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * tweak_server 电池采样：与主进程 [io.github.lumkit.tweak.common.utils.BatteryUtils] 共用 [BatterySnapshotCore]。
 * 需在进程启动时 [io.github.lumkit.tweak.sharednative.BatteryBridge.init]。
 */
class AppProcessAlignedBatterySampler : BatterySampler {

    override fun sample(): BatterySample? = runBlocking {
        val snapshot = BatterySnapshotCore.readSnapshot(::readSysfsText)
        val level = snapshot.capacityPercent ?: return@runBlocking null
        if (level !in 0..100) return@runBlocking null

        val charging = BatterySamplingChargeState.isCharging()
        BatterySample(
            timestampMs = System.currentTimeMillis(),
            level = level,
            voltageMv = snapshot.voltageMv ?: Int.MIN_VALUE,
            currentMa = snapshot.currentMa ?: Int.MIN_VALUE,
            tempCenti = snapshot.temperatureCelsius?.let { (it * 100f).toInt().toShort() }
                ?: Short.MIN_VALUE,
            screenOn = ScreenStateReader.isInteractive(),
            state = if (charging) 1 else 0,
        )
    }

    private suspend fun readSysfsText(path: String): String? =
        runCatching { File(path).readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    private object BatterySamplingChargeState {
        fun isCharging(): Boolean {
            val status = BatteryBridge.getStatus()
            if (status == Int.MIN_VALUE) {
                return false
            }
            val plugged = BatteryBridge.isPlugged()
            return when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> true
                BatteryManager.BATTERY_STATUS_FULL -> plugged
                else -> false
            }
        }
    }
}
