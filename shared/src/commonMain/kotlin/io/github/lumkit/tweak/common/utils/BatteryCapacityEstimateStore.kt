package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.database.battery.BatteryChargeState
import io.github.lumkit.tweak.common.database.battery.repos.BatteryRecordRepository

object BatteryCapacityEstimateStore {

    suspend fun refreshFromChargingHistory(
        repository: BatteryRecordRepository = BatteryRecordRepository(),
    ): Int? {
        val chargingSessions = repository.queryChargingSessions()
            .filter { session ->
                session.chargeState == BatteryChargeState.CHARGING &&
                    session.confirmed &&
                    !session.deleted &&
                    session.endedAt != null
            }
        if (chargingSessions.isEmpty()) {
            TweakDataStore.setEstimatedBatteryFullCapacityMah(null)
            return null
        }

        val estimatedMah = BatteryFullCapacityEstimator.estimateMah(
            chargingSessions.map { session ->
                session to repository.querySamplesBySessionId(session.id)
            },
        )
        TweakDataStore.setEstimatedBatteryFullCapacityMah(estimatedMah)
        return estimatedMah
    }
}
