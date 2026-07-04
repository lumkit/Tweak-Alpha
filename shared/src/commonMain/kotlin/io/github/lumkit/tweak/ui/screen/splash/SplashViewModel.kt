package io.github.lumkit.tweak.ui.screen.splash

import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.LibSuX
import io.github.lumkit.tweak.common.utils.ShizukuX
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.startKeepAliveService
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SplashViewModel: BaseViewModel() {

    private val _checkLoadingState = MutableStateFlow(false)
    val checkLoadingState: StateFlow<Boolean> = _checkLoadingState.asStateFlow()

    val runtimeModeState = GlobalViewModel.runtimeModeState

    suspend fun setRuntimeMode(runtimeMode: RuntimeMode) {
        TweakDataStore.setRuntimeMode(runtimeMode)
    }

    suspend fun checkRuntime(
        block: () -> Unit
    ) {
        val mode = runtimeModeState.value
        if (mode == null || mode == RuntimeMode.Unknow) return

        when (mode) {
            RuntimeMode.Root -> {
                if (!checkRootMode()) {
                    setRuntimeMode(RuntimeMode.Unknow)
                    return
                }
                initAppService()
                block()
            }
            RuntimeMode.Shizuku -> {
                if (!checkShizukuMode()) {
                    setRuntimeMode(RuntimeMode.Unknow)
                    return
                }
                initAppService()
                block()
            }
        }
    }

    suspend fun checkRootMode(): Boolean {
        _checkLoadingState.value = true
        return try {
            LibSuX.checkRoot()
        } finally {
            _checkLoadingState.value = false
        }
    }

    suspend fun checkShizukuMode(): Boolean {
        _checkLoadingState.value = true
        return try {
            ShizukuX.checkShizuku().let {
                if (!it) {
                    ShizukuX.requestPermission()
                } else {
                    true
                }
            }
        } finally {
            _checkLoadingState.value = false
        }
    }

    private fun initAppService() {
        // START KeepAlive
        startKeepAliveService(true)
    }
}