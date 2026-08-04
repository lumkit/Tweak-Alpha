package io.github.lumkit.tweak.server.battery

import io.github.lumkit.tweak.common.daemon.DaemonPaths
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 电池采样 + v2 日志会话状态机（对齐原 tweakd battery_log）。
 */
class BatteryEngine(
    private val confPath: String,
    private val logsDir: File,
    private val sampler: BatterySampler,
    private val packageReader: ForegroundPackageReader = ForegroundPackageReader(),
) {
    private val running = AtomicBoolean(false)
    private val reloadRequested = AtomicBoolean(false)
    private val confRef = AtomicReference(BatteryRecordConf())
    private var thread: Thread? = null
    private var writer: BrlogWriter? = null
    private var applogWriter: ApplogWriter? = null
    private var uidpowWriter: UidpowWriter? = null
    private val uidpowCaptureInFlight = AtomicBoolean(false)

    @Volatile
    var lastStatusLine: String = "battery_enabled=0 battery_interval_ms=0"
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        logsDir.mkdirs()
        thread = Thread({
            runCatching { loop() }
                .onFailure { logE("battery engine fatal: ${it.message}", it, TAG) }
                .also {
                    running.set(false)
                    logD("battery engine stopped", TAG)
                }
        }, "tweak-battery").also {
            it.isDaemon = true
            it.start()
        }
        logD("battery engine started", TAG)
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    fun reloadConfig() {
        reloadRequested.set(true)
        thread?.interrupt()
    }

    private fun loop() {
        var active: ActiveSession? = null
        try {
            while (running.get()) {
                val cfg = BatteryRecordConf.read(confPath)
                confRef.set(cfg)
                BatteryReadingNormalize.updateCalibration(
                    BatteryReadingNormalize.CurrentCalibration(
                        dualCell = cfg.dualCell,
                        scale = cfg.currentScale,
                    ),
                )
                lastStatusLine =
                    "battery_enabled=${if (cfg.enabled) 1 else 0} battery_interval_ms=${cfg.intervalMs}" +
                        " dual_cell=${if (cfg.dualCell) 1 else 0} current_scale=${cfg.currentScale}"

                if (!cfg.enabled) {
                    active = endActive(active, System.currentTimeMillis())
                    sleepInterruptible(3_000L)
                    continue
                }

                val now = System.currentTimeMillis()
                val sample = sampler.sample()
                if (sample == null || !sample.hasValidLevel) {
                    logD("sample skipped null_or_invalid_level", TAG)
                    sleepInterruptible(cfg.intervalMs.toLong())
                    continue
                }
                logD(
                    "sample level=${sample.level}% " +
                        "V=${fmtMv(sample.voltageMv)} " +
                        "I=${fmtMa(sample.currentMa)} " +
                        "T=${fmtTemp(sample.tempCenti)} " +
                        "state=${sample.state} screen=${sample.screenOn}",
                    TAG,
                )

                val newState = sample.state
                if (active == null) {
                    active = resumeOpenSession(newState, cfg) ?: beginSession(now, newState, cfg)
                } else if (active.state != newState) {
                    if (active.state == BatteryChargeState.CHARGING.code && !active.confirmed) {
                        writer?.markDeleted()
                        active.deleted = true
                    }
                    active = endActive(active, now)
                    active = beginSession(now, newState, cfg)
                }

                val session = active
                if (session.state == BatteryChargeState.CHARGING.code &&
                    !session.confirmed &&
                    !session.deleted
                ) {
                    if (now - session.startedAt >= session.confirmMs) {
                        session.confirmed = true
                        writer?.markConfirmed()
                    }
                }

                writer?.appendSample(sample.copy(timestampMs = now, state = newState))
                val pkg = runCatching { packageReader.resolve() }.getOrDefault("")
                applogWriter?.append(now, pkg)
                maybeCaptureUidpowPeriodic(session, now)
                sleepInterruptible(cfg.intervalMs.toLong())
            }
        } finally {
            endActive(active, System.currentTimeMillis())
        }
    }

    private fun maybeCaptureUidpowPeriodic(session: ActiveSession, now: Long) {
        if (session.state != BatteryChargeState.DISCHARGING.code) return
        val uw = uidpowWriter ?: return
        if (now - session.lastUidpowCaptureAtMs < UidpowPeriodicIntervalMs) return
        if (!uidpowCaptureInFlight.compareAndSet(false, true)) return
        session.lastUidpowCaptureAtMs = now
        Thread({
            try {
                runCatching {
                    UidPowerSampler().capture(now)?.let { frame ->
                        // 会话可能已结束并换了 writer；仅当前 writer 仍挂着时写入
                        if (uidpowWriter === uw) {
                            uw.appendFrame(frame)
                        }
                    }
                }.onFailure { logE("uidpow periodic failed: ${it.message}", it, TAG) }
            } finally {
                uidpowCaptureInFlight.set(false)
            }
        }, "uidpow-periodic").apply {
            isDaemon = true
            start()
        }
    }

    private fun beginSession(now: Long, state: Int, cfg: BatteryRecordConf): ActiveSession {
        val session = ActiveSession(
            startedAt = now,
            createdAt = now,
            intervalMs = cfg.intervalMs,
            state = state,
            confirmMs = DefaultConfirmMs,
            confirmed = BatteryChargeState.fromCode(state).defaultConfirmed,
            deleted = false,
        )
        val w = BrlogWriter(logsDir, cfg.maxPartBytes)
        w.openSession(
            BrlogWriter.SessionMeta(
                startedAt = session.startedAt,
                intervalMs = session.intervalMs,
                state = session.state,
                confirmMs = session.confirmMs,
                createdAt = session.createdAt,
                confirmed = session.confirmed,
                deleted = session.deleted,
            ),
        )
        writer = w
        val aw = ApplogWriter(logsDir, cfg.maxPartBytes)
        aw.openSession(
            ApplogWriter.SessionMeta(
                startedAt = session.startedAt,
                state = session.state,
                intervalMs = session.intervalMs,
                createdAt = session.createdAt,
            ),
        )
        applogWriter = aw
        if (state == BatteryChargeState.DISCHARGING.code) {
            val uw = UidpowWriter(logsDir)
            uw.openSession(session.startedAt, session.state)
            uidpowWriter = uw
            session.lastUidpowCaptureAtMs = now
            Thread({
                runCatching {
                    UidPowerSampler().capture()?.let { uw.appendFrame(it) }
                }.onFailure { logE("uidpow baseline failed: ${it.message}", it, TAG) }
            }, "uidpow-baseline").apply {
                isDaemon = true
                start()
            }
        }
        return session
    }

    private fun resumeOpenSession(state: Int, cfg: BatteryRecordConf): ActiveSession? {
        val meta = findLatestOpenSessionMeta(state) ?: return null
        val session = ActiveSession(
            startedAt = meta.startedAt,
            createdAt = meta.createdAt,
            intervalMs = meta.intervalMs,
            state = meta.state,
            confirmMs = meta.confirmMs,
            confirmed = meta.confirmed,
            deleted = meta.deleted,
        )
        val w = BrlogWriter(logsDir, cfg.maxPartBytes)
        w.openSession(meta)
        writer = w

        val aw = ApplogWriter(logsDir, cfg.maxPartBytes)
        aw.openSession(
            ApplogWriter.SessionMeta(
                startedAt = meta.startedAt,
                state = meta.state,
                intervalMs = meta.intervalMs,
                createdAt = meta.createdAt,
                endedAt = meta.endedAt,
            ),
        )
        applogWriter = aw

        if (state == BatteryChargeState.DISCHARGING.code) {
            val uw = UidpowWriter(logsDir)
            uw.openSession(meta.startedAt, meta.state)
            uidpowWriter = uw
        }
        logD("resumed open session startedAt=${meta.startedAt} state=${meta.state}", TAG)
        return session
    }

    private fun findLatestOpenSessionMeta(state: Int): BrlogWriter.SessionMeta? {
        val candidates = logsDir.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.name.contains(".brlog") &&
                    file.name.startsWith("_ignore_").not()
            }
            ?.mapNotNull { file -> readSessionMeta(file) }
            ?.filter { meta ->
                meta.state == state &&
                    meta.endedAt <= 0L &&
                    !meta.deleted
            }
            ?.sortedByDescending { it.startedAt }
            ?: return null
        return candidates.firstOrNull()
    }

    private fun readSessionMeta(file: File): BrlogWriter.SessionMeta? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < BrlogWriter.HEADER_SIZE.toLong()) return null
                val bytes = ByteArray(BrlogWriter.HEADER_SIZE.toInt())
                raf.readFully(bytes)
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                if (
                    bytes[0] != 'T'.code.toByte() ||
                    bytes[1] != 'W'.code.toByte() ||
                    bytes[2] != 'B'.code.toByte() ||
                    bytes[3] != '2'.code.toByte()
                ) {
                    return null
                }
                buf.position(4)
                val version = buf.short.toInt() and 0xFFFF
                if (version != BrlogWriter.VERSION.toInt()) return null
                buf.short // header size
                buf.short // record size
                buf.short // flags
                val intervalMs = buf.int
                val state = buf.int
                val confirmMs = buf.int
                val startedAt = buf.long
                val createdAt = buf.long
                val confirmed = buf.get().toInt() == 1
                val deleted = buf.get().toInt() == 1
                buf.short // pad
                val endedAt = buf.long
                BrlogWriter.SessionMeta(
                    startedAt = startedAt,
                    intervalMs = intervalMs,
                    state = state,
                    confirmMs = confirmMs,
                    createdAt = createdAt,
                    confirmed = confirmed,
                    deleted = deleted,
                    endedAt = endedAt,
                )
            }
        }.getOrNull()
    }

    private fun endActive(active: ActiveSession?, endedAt: Long): ActiveSession? {
        if (active == null) return null
        val uw = uidpowWriter
        val startedAt = active.startedAt
        val state = active.state
        writer?.closeSession(endedAt)
        writer = null
        applogWriter?.closeSession(endedAt)
        applogWriter = null
        uidpowWriter = null
        if (uw != null && state == BatteryChargeState.DISCHARGING.code) {
            Thread({
                runCatching {
                    UidPowerSampler().capture(endedAt)?.let { uw.appendFrame(it) }
                }.onFailure {
                    logE("uidpow end failed startedAt=$startedAt: ${it.message}", it, TAG)
                }.also {
                    uw.closeSession()
                }
            }, "uidpow-end").apply {
                isDaemon = true
                start()
            }
        }
        return null
    }

    private fun sleepInterruptible(ms: Long) {
        try {
            var left = ms
            while (left > 0 && running.get()) {
                if (reloadRequested.compareAndSet(true, false)) return
                val slice = minOf(left, 500L)
                Thread.sleep(slice)
                left -= slice
            }
        } catch (_: InterruptedException) {
            reloadRequested.set(false)
        }
    }

    private data class ActiveSession(
        val startedAt: Long,
        val createdAt: Long,
        val intervalMs: Int,
        val state: Int,
        val confirmMs: Int,
        var confirmed: Boolean,
        var deleted: Boolean,
        var lastUidpowCaptureAtMs: Long = 0L,
    )

    companion object {
        private const val TAG = "BatteryEngine"
        /** 放电中 batterystats 中间帧间隔（避免与 brlog 同频）。 */
        private const val UidpowPeriodicIntervalMs = 10L * 60L * 1000L

        private const val DefaultConfirmMs = 5_000

        private fun fmtMv(voltageMv: Int): String =
            if (voltageMv == Int.MIN_VALUE) "n/a" else "${voltageMv}mV"

        private fun fmtMa(currentMa: Int): String =
            if (currentMa == Int.MIN_VALUE) "n/a" else "${currentMa}mA"

        private fun fmtTemp(tempCenti: Short): String =
            if (tempCenti == Short.MIN_VALUE) {
                "n/a"
            } else {
                val v = tempCenti / 100.0
                val rounded = (kotlin.math.round(v * 100.0) / 100.0)
                "${rounded}C"
            }

        fun createDefault(daemonDir: File): BatteryEngine {
            val conf = File(daemonDir, DaemonPaths.BATTERY_RECORD_CONF_NAME).absolutePath
            val logs = File(daemonDir, DaemonPaths.BATTERY_LOGS_DIR_NAME)
            val sampler = AppProcessAlignedBatterySampler()
            logD("sampler=${sampler::class.java.simpleName}", TAG)
            return BatteryEngine(conf, logs, sampler)
        }
    }
}
