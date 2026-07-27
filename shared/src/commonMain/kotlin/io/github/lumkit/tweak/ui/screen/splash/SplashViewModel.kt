package io.github.lumkit.tweak.ui.screen.splash

import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.LibSuX
import io.github.lumkit.tweak.common.utils.PrivilegedAppInitializer
import io.github.lumkit.tweak.common.utils.ShizukuX
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.ensureAccessibilityServiceEnabled
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SplashViewModel : BaseViewModel() {

    private val _checkLoadingState = MutableStateFlow(false)
    val checkLoadingState: StateFlow<Boolean> = _checkLoadingState.asStateFlow()

    val runtimeModeState = GlobalViewModel.runtimeModeState

    init {
        GlobalViewModel.create()
    }

    suspend fun setRuntimeMode(runtimeMode: RuntimeMode) {
        TweakDataStore.setRuntimeMode(runtimeMode)
    }

    /**
     * 自动检测：全程展示 [checkLoadingState]（含特权初始化）。
     */
    suspend fun checkRuntime(
        block: () -> Unit
    ) {
        val mode = runtimeModeState.value
        if (mode == null || mode == RuntimeMode.Unknow) return

        withCheckLoading {
            when (mode) {
                RuntimeMode.Root -> {
                    if (!checkRootMode()) {
                        setRuntimeMode(RuntimeMode.Unknow)
                        return@withCheckLoading
                    }
                    onPrivilegeReady()
                    block()
                }

                RuntimeMode.Shizuku -> {
                    if (!checkShizukuMode()) {
                        setRuntimeMode(RuntimeMode.Unknow)
                        return@withCheckLoading
                    }
                    onPrivilegeReady()
                    block()
                }

                RuntimeMode.Unknow -> Unit
            }
        }
    }

    /**
     * 手动选模式：校验 + 特权初始化全程展示加载 Dialog。
     * @return 是否校验并初始化成功（失败时由调用方弹 Snackbar）
     */
    suspend fun proceedWithRuntimeMode(mode: RuntimeMode): Boolean {
        if (mode == RuntimeMode.Unknow) return false
        return withCheckLoading {
            when (mode) {
                RuntimeMode.Root -> {
                    if (!checkRootMode()) return@withCheckLoading false
                    setRuntimeMode(RuntimeMode.Root)
                    onPrivilegeReady()
                    true
                }

                RuntimeMode.Shizuku -> {
                    if (!checkShizukuMode()) return@withCheckLoading false
                    setRuntimeMode(RuntimeMode.Shizuku)
                    onPrivilegeReady()
                    true
                }

                RuntimeMode.Unknow -> false
            }
        }
    }

    suspend fun checkRootMode(): Boolean = LibSuX.checkRoot()

    suspend fun checkShizukuMode(): Boolean =
        ShizukuX.checkShizuku().let { granted ->
            if (!granted) {
                ShizukuX.requestPermission()
            } else {
                true
            }
        }

    /**
     * 特权校验通过后调用（Splash 自动检测与手动选模式共用）。
     * 与 Splash composition 生命周期解耦，避免跳转 Main 时被取消。
     */
    suspend fun onPrivilegeReady() = withContext(NonCancellable + Dispatchers.IO) {
        PrivilegedAppInitializer.onPrivilegeReady()
        ensureAccessibilityServiceEnabled()
    }

    private suspend fun <T> withCheckLoading(block: suspend () -> T): T {
        _checkLoadingState.value = true
        return try {
            block()
        } finally {
            _checkLoadingState.value = false
        }
    }
}
