package io.github.lumkit.tweak.server.battery

import android.os.BatteryManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.logW
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * 通过 `batteryproperties` Binder + `battery.dump` 取样本（对齐 BatteryRecorder DumpsysSampler）。
 * 全部反射，避免 Hidden API stub 打包冲突。
 */
class BinderBatterySampler : BatterySampler {

    private val batteryService: IBinder? = serviceBinder("battery")
    private val registrar: Any? = runCatching {
        val raw = serviceBinder("batteryproperties") ?: return@runCatching null
        Class.forName("android.os.IBatteryPropertiesRegistrar\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, raw)
    }.onFailure {
        logW("registrar unavailable: ${it.message}", TAG)
    }.getOrNull()

    private val propertyCtor = runCatching {
        Class.forName("android.os.BatteryProperty").getConstructor()
    }.getOrNull()

    init {
        logD("init registrar=${registrar != null} battery=${batteryService != null}", TAG)
    }

    override fun sample(): BatterySample? {
        var currentUa = Long.MIN_VALUE
        var capacity = -1
        var rawStatus = BatteryManager.BATTERY_STATUS_UNKNOWN
        var voltageMv = Int.MIN_VALUE
        var tempTenths = Int.MIN_VALUE
        var plugged = false
        var pluggedKnown = false

        runCatching {
            val prop = propertyCtor?.newInstance() ?: return@runCatching
            if (registrar != null) {
                getLongProperty(registrar, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, prop)?.let {
                    currentUa = it
                }
                getLongProperty(registrar, BatteryManager.BATTERY_PROPERTY_CAPACITY, prop)?.let {
                    capacity = it.toInt()
                }
                getLongProperty(registrar, BatteryManager.BATTERY_PROPERTY_STATUS, prop)?.let {
                    rawStatus = it.toInt()
                }
            }
        }.onFailure {
            logE("getProperty failed: ${it.message}", it, TAG)
        }

        parseBatteryDump()?.let { dump ->
            if (voltageMv == Int.MIN_VALUE && dump.voltageMv != Int.MIN_VALUE) {
                voltageMv = dump.voltageMv
            }
            if (tempTenths == Int.MIN_VALUE && dump.tempTenths != Int.MIN_VALUE) {
                tempTenths = dump.tempTenths
            }
            if (capacity < 0 && dump.level >= 0) capacity = dump.level
            if (rawStatus == BatteryManager.BATTERY_STATUS_UNKNOWN && dump.status >= 0) {
                rawStatus = dump.status
            }
            if (currentUa == Long.MIN_VALUE && dump.currentUa != Long.MIN_VALUE) {
                currentUa = dump.currentUa
            }
            if (dump.pluggedKnown) {
                pluggedKnown = true
                plugged = dump.plugged
            }
        }

        if (capacity !in 0..100) return null
        val charging = resolveChargingState(rawStatus, plugged, pluggedKnown)

        return BatterySample(
            timestampMs = System.currentTimeMillis(),
            level = capacity,
            voltageMv = voltageMv,
            currentMa = if (currentUa == Long.MIN_VALUE) Int.MIN_VALUE else uaToMa(currentUa),
            tempCenti = if (tempTenths == Int.MIN_VALUE) {
                Short.MIN_VALUE
            } else {
                (tempTenths * 10).toShort()
            },
            screenOn = ScreenStateReader.isInteractive(),
            state = if (charging) 1 else 0,
        )
    }

    private fun getLongProperty(registrar: Any, id: Int, prop: Any): Long? {
        val method = registrar.javaClass.methods.firstOrNull {
            it.name == "getProperty" && it.parameterTypes.size == 2
        } ?: return null
        method.invoke(registrar, id, prop)
        val getLong = prop.javaClass.methods.firstOrNull {
            it.name == "getLong" && it.parameterTypes.isEmpty()
        } ?: return null
        return getLong.invoke(prop) as? Long
    }

    private fun parseBatteryDump(): DumpFields? {
        val binder = batteryService ?: return null
        return runCatching {
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]
            val dumpThread = Thread {
                try {
                    val dump = binder.javaClass.methods.firstOrNull {
                        it.name == "dump" && it.parameterTypes.size >= 2
                    }
                    dump?.invoke(binder, writeSide.fileDescriptor, emptyArray<String>())
                } catch (_: Throwable) {
                } finally {
                    runCatching { writeSide.close() }
                }
            }
            dumpThread.start()
            var voltageMv = Int.MIN_VALUE
            var tempTenths = Int.MIN_VALUE
            var level = -1
            var status = -1
            var currentUa = Long.MIN_VALUE
            var inState = false
            var plugged = false
            var pluggedKnown = false
            ParcelFileDescriptor.AutoCloseInputStream(readSide).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.contains("Current Battery Service state:")) {
                            inState = true
                            continue
                        }
                        if (!inState) continue
                        val lower = line.lowercase()
                        when {
                            line.contains("voltage:") ->
                                line.substringAfter(':').trim().toIntOrNull()?.let { voltageMv = it }
                            line.contains("temperature:") ->
                                line.substringAfter(':').trim().toIntOrNull()?.let { tempTenths = it }
                            line.contains("level:") ->
                                line.substringAfter(':').trim().toIntOrNull()?.let { level = it }
                            line.contains("status:") ->
                                line.substringAfter(':').trim().toIntOrNull()?.let { status = it }
                            line.contains("current now:", ignoreCase = true) ->
                                line.substringAfter(':').trim().toLongOrNull()?.let { currentUa = it }
                            lower.startsWith("ac powered:") ||
                                lower.startsWith("usb powered:") ||
                                lower.startsWith("wireless powered:") ||
                                lower.startsWith("dock powered:") -> {
                                pluggedKnown = true
                                val v = line.substringAfter(':').trim().lowercase()
                                if (v == "true" || v == "1") plugged = true
                            }
                        }
                    }
                }
            }
            dumpThread.join(2_000)
            DumpFields(voltageMv, tempTenths, level, status, currentUa, plugged, pluggedKnown)
        }.onFailure {
            logW("battery dump parse failed: ${it.message}", TAG)
        }.getOrNull()
    }

    private data class DumpFields(
        val voltageMv: Int,
        val tempTenths: Int,
        val level: Int,
        val status: Int,
        val currentUa: Long,
        val plugged: Boolean,
        val pluggedKnown: Boolean,
    )

    companion object {
        private const val TAG = "BinderBatterySampler"

        private fun serviceBinder(name: String): IBinder? = runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, name) as? IBinder
        }.getOrNull()

        private fun uaToMa(ua: Long): Int =
            if (abs(ua) < 10_000L) ua.toInt() else (ua / 1000L).toInt()

        /**
         * 充电会话判定：正在 CHARGING；FULL 时仅在确认仍插入电源时保持充电 session。
         * 拔线后 status 可能短暂仍为 FULL，依赖 plugged 结束充电 session。
         */
        fun resolveChargingState(
            status: Int,
            plugged: Boolean,
            pluggedKnown: Boolean = true,
        ): Boolean =
            when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> true
                BatteryManager.BATTERY_STATUS_FULL -> if (pluggedKnown) plugged else true
                else -> false
            }
    }
}
