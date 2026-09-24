package io.github.lumkit.tweak.model

import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object RuntimeModeStore {
    /**
     * 与原来的 viewModelScope 一样用 Main.immediate，主线程上第一次读取会立刻开始收集。
     * Eagerly：Daemon / Files / Shell 会在没有界面收集者时读当前值，
     * WhileSubscribed 可能长期停在 null。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val mode: StateFlow<RuntimeMode?> = TweakDataStore.runtimeModeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    /**
     * 解析当前运行模式。DataStore 在 Direct Boot / 文件锁异常时可能一直不发出值，
     * 调用方不能无限等待。
     */
    suspend fun current(timeout: Duration = 2.seconds): RuntimeMode {
        mode.value?.let { return it }
        return withTimeoutOrNull(timeout) {
            mode.filterNotNull().first()
        } ?: withTimeoutOrNull(timeout) {
            TweakDataStore.runtimeModeFlow().first()
        } ?: RuntimeMode.Unknow
    }
}
