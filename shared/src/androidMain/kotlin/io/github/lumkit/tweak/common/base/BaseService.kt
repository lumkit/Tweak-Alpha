package io.github.lumkit.tweak.common.base

import android.app.Service
import android.content.Intent
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

abstract class BaseService: Service() {

    private val startCommandState: Int
        get() = runBlocking {
            if (TweakDataStore.autoStartAppSwitchFlow().first()) {
                START_STICKY
            } else {
                START_NOT_STICKY
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return startCommandState
    }
}