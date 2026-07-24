package io.github.lumkit.tweak.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.base.BaseService
import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.repos.BatteryRecordRepository
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.utils.BatteryUtils
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.service.BatteryRecordService.Companion.ACTION_SAMPLE
import io.github.lumkit.tweak.service.BatteryRecordService.Companion.ACTION_STOP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * 电池记录采样服务。
 *
 * 由 Daemon / 特权 `am startservice` 按采样间隔反复带 [ACTION_SAMPLE] 唤起；
 * Service 可常驻，采完一笔即返回，不在单次采样后 [stopSelf]。
 * 需停止时发 [ACTION_STOP]。
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
 */
class BatteryRecordService : BaseService() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "BatteryRecordService"

        const val ACTION_SAMPLE = "io.github.lumkit.tweak.action.BATTERY_RECORD_SAMPLE"
        const val ACTION_STOP = "io.github.lumkit.tweak.action.BATTERY_RECORD_STOP"

        fun sample(context: Context = application) {
            val intent = Intent(context, BatteryRecordService::class.java).apply {
                action = ACTION_SAMPLE
            }
            context.startService(intent)
        }

        fun stop(context: Context = application) {
            val intent = Intent(context, BatteryRecordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val sampleMutex = Mutex()
    private val repository by lazy { BatteryRecordRepository() }

    private val powerManager by lazy {
        getSystemService(POWER_SERVICE) as PowerManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SAMPLE -> {
                serviceScope.launch {
                    sampleMutex.withLock {
                        runCatching { sampleOnce() }
                            .onFailure { logE(it.stackTraceToString(), it, TAG) }
                    }
                }
            }

            ACTION_STOP -> {
                logD("ACTION_STOP", TAG)
                stopSelf()
            }

            else -> Unit
        }
        // 被杀后不空着重启，等下次带 action 再来
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun sampleOnce() {
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
                "current=${snapshot.currentMa} screenOn=${powerManager.isInteractive}",
            TAG,
        )
    }

    /**
     * 保证存在与 [chargeState] 匹配的进行中 session，返回其 id。
     */
    private suspend fun ensureActiveSession(
        chargeState: BatteryChargeState,
        now: Long,
    ): Long {
        val active = repository.queryActiveSession()
        if (active == null) {
            val intervalMs = TweakDataStore.batteryRecordSampleIntervalMsFlow().first()
            return repository.startSession(
                state = chargeState,
                intervalMs = intervalMs,
                startedAt = now,
            )
        }

        if (active.chargeState == chargeState) {
            return active.id
        }

        // 状态切换：结束或丢弃旧 session，再开新 session
        if (active.chargeState == BatteryChargeState.CHARGING && !active.confirmed) {
            repository.softDeleteUnconfirmedSession(active.id, endedAt = now)
        } else {
            repository.endSession(active.id, endedAt = now)
        }

        val intervalMs = TweakDataStore.batteryRecordSampleIntervalMsFlow().first()
        return repository.startSession(
            state = chargeState,
            intervalMs = intervalMs,
            startedAt = now,
        )
    }

    private suspend fun maybeConfirmChargingSession(sessionId: Long, now: Long) {
        val session = repository.querySessionById(sessionId) ?: return
        if (session.chargeState != BatteryChargeState.CHARGING || session.confirmed) return
        if (now - session.startedAt >= session.confirmMs) {
            repository.confirmSession(sessionId)
            logD("charging session confirmed id=$sessionId", TAG)
        }
    }

    private fun resolveChargeState(): BatteryChargeState {
        val batteryIntent = registerReceiver(
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
