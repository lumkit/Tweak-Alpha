package io.github.lumkit.tweak.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.repos.BatteryRecordRepository
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.model.GlobalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * 电池记录采样器。
 *
 * 会话状态机：
 * ```
 * 放电采样中 ──检测到充电──▶ 新建充电 session(confirmed=0)
 *                               │
 *                     ┌─────────┴─────────┐
 *                  满 confirmMs 仍充电   窗口内变回放电
 *                     ▼                   ▼
 *               confirmed=1         软删该充电 session
 * 充电采样中 ──检测到放电──▶ 结束充电 session，新建放电 session
 * ```
 *
 * 由 [TweakAccessibilityService] 连接后 [start]，销毁时 [stop]。
 */
class BatteryRecordSampler(
    private val context: Context,
) {
    companion object {
        private const val TAG = "BatteryRecordSampler"
    }

    private val sampleMutex = Mutex()
    private val repository by lazy { BatteryRecordRepository() }
    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private var supervisor: Job? = null
    private var loopJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (loopJob?.isActive == true) return
        val job = SupervisorJob()
        supervisor = job
        val scope = CoroutineScope(job + Dispatchers.IO)
        isRunning = true
        loopJob = scope.launch {
            logD("sample loop started", TAG)
            while (isActive) {
                sampleMutex.withLock {
                    runCatching { sampleOnce() }
                        .onFailure { logE(it.stackTraceToString(), it, TAG) }
                }
                val intervalMs = GlobalViewModel.batteryRecordSampleIntervalMsState.value
                    .coerceAtLeast(100)
                delay(intervalMs.milliseconds)
            }
        }
    }

    fun stop() {
        isRunning = false
        loopJob?.cancel()
        loopJob = null
        supervisor?.cancel()
        supervisor = null
        logD("sample loop stopped", TAG)
    }

    /** 执行一次采样并写入数据库 */
    suspend fun sampleOnce() {
        val now = Clock.System.now().toEpochMilliseconds()
        val chargeState = resolveChargeState()
        val snapshot = BatteryUtils.getSnapshot()
        val level = snapshot.capacityPercent
        if (level == null) {
            logD("skip sample: capacity unavailable", TAG)
            return
        }

        val sessionId = ensureActiveSession(chargeState, now)
        maybeConfirmChargingSession(sessionId, now)

        repository.insertSample(
            BatteryRecordSampleEntity(
                sessionId = sessionId,
                timestamp = now,
                level = level,
                voltageMv = snapshot.voltageMv,
                temperatureC = snapshot.temperatureCelsius,
                screenOn = powerManager.isInteractive,
                currentMa = snapshot.currentMa,
            ),
        )
        logD(
            "sampled session=$sessionId state=$chargeState level=$level " +
                "current=${snapshot.currentMa} screenOn=${powerManager.isInteractive} voltageMv=${snapshot.voltageMv}",
            TAG,
        )
    }

    private suspend fun ensureActiveSession(
        chargeState: BatteryChargeState,
        now: Long,
    ): Long {
        val active = repository.queryActiveSession()
        if (active == null) {
            return repository.startSession(
                state = chargeState,
                intervalMs = sampleIntervalMs(),
                startedAt = now,
            )
        }

        if (active.chargeState == chargeState) {
            return active.id
        }

        if (active.chargeState == BatteryChargeState.CHARGING && !active.confirmed) {
            repository.softDeleteUnconfirmedSession(active.id, endedAt = now)
        } else {
            repository.endSession(active.id, endedAt = now)
        }

        return repository.startSession(
            state = chargeState,
            intervalMs = sampleIntervalMs(),
            startedAt = now,
        )
    }

    private fun sampleIntervalMs(): Int =
        GlobalViewModel.batteryRecordSampleIntervalMsState.value.coerceAtLeast(100)

    private suspend fun maybeConfirmChargingSession(sessionId: Long, now: Long) {
        val session = repository.querySessionById(sessionId) ?: return
        if (session.chargeState != BatteryChargeState.CHARGING || session.confirmed) return
        if (now - session.startedAt >= session.confirmMs) {
            repository.confirmSession(sessionId)
            logD("charging session confirmed id=$sessionId", TAG)
        }
    }

    private fun resolveChargeState(): BatteryChargeState {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return BatteryChargeState.DISCHARGING

        val status = batteryIntent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        )
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL,
            -> BatteryChargeState.CHARGING

            else -> BatteryChargeState.DISCHARGING
        }
    }
}
