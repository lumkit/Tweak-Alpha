package io.github.lumkit.tweak.server.battery

import io.github.lumkit.tweak.common.daemon.DaemonPaths
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.utils.BatteryReadingNormalize
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 电池采样 + v2 日志会话状态机（对齐原 tweakd battery_log）。
 */
class BatteryEngine(
    private val confPath: String,
    private val logsDir: File,
    private val sampler: BatterySampler,
) {
    private val running = AtomicBoolean(false)
    private val reloadRequested = AtomicBoolean(false)
    private val confRef = AtomicReference(BatteryRecordConf())
    private var thread: Thread? = null
    private var writer: BrlogWriter? = null

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
                    sleepInterruptible(cfg.intervalMs.toLong())
                    continue
                }

                val newState = sample.state
                if (active == null) {
                    active = beginSession(now, newState, cfg)
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
                sleepInterruptible(cfg.intervalMs.toLong())
            }
        } finally {
            endActive(active, System.currentTimeMillis())
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
        return session
    }

    private fun endActive(active: ActiveSession?, endedAt: Long): ActiveSession? {
        if (active == null) return null
        writer?.closeSession(endedAt)
        writer = null
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
    )

    companion object {
        private const val TAG = "BatteryEngine"

        fun createDefault(daemonDir: File): BatteryEngine {
            val conf = File(daemonDir, DaemonPaths.BATTERY_RECORD_CONF_NAME).absolutePath
            val logs = File(daemonDir, DaemonPaths.BATTERY_LOGS_DIR_NAME)
            val sampler = AppProcessAlignedBatterySampler()
            logD("sampler=${sampler::class.java.simpleName}", TAG)
            return BatteryEngine(conf, logs, sampler)
        }
    }
}
