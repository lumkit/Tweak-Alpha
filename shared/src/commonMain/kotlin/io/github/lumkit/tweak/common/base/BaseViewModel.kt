package io.github.lumkit.tweak.common.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.utils.convert
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.logI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

abstract class BaseViewModel : ViewModel() {

    @Serializable
    sealed class LoadState(open val message: String? = null) {

        data class Loading(
            override val message: String? = null
        ) : LoadState(message)

        data class Success(
            override val message: String? = null
        ) : LoadState(message)

        data class Failure(
            override val message: String? = null
        ) : LoadState(message)
    }

    interface LoadStateCoroutineScope {
        suspend fun loading(message: String? = null)
        suspend fun success(message: String? = null)
        suspend fun failure(throwable: Throwable? = null)
    }

    /**
     * 启动一个协程来执行代码块
     */
    fun launch(
        context: CoroutineContext = Dispatchers.Main,
        block: suspend () -> Unit,
        error: suspend (Throwable) -> Unit = {},
        complete: suspend () -> Unit = {}
    ) = viewModelScope.launch(context) {
        try {
            block()
        } catch (e: Throwable) {
            logE("launch failed", e, "BaseViewModel")
            error(e)
        } finally {
            complete()
        }
    }

    /**
     * 启动一个协程来异步执行代码
     * @param slot 加载状态槽位
     * @param failed 失败回调
     * @param complete 完成回调
     * @param block 代码块
     */
    fun suspendLaunch(
        slot: LoadSlot,
        context: CoroutineContext = Dispatchers.Main,
        failed: suspend LoadStateCoroutineScope.(Throwable) -> Unit = { failure(it) },
        complete: suspend () -> Unit = {},
        block: suspend LoadStateCoroutineScope.() -> Unit,
    ) {
        val scope = object : LoadStateCoroutineScope {
            override suspend fun loading(message: String?) {
                slot.setState(LoadState.Loading(message = message))
            }

            override suspend fun success(message: String?) {
                slot.setState(LoadState.Success(message = message))
            }

            override suspend fun failure(throwable: Throwable?) {
                slot.setState(LoadState.Failure(message = throwable?.message))
            }
        }
        viewModelScope.launch(context) {
            try {
                block(scope)
            } catch (e: Exception) {
                logE("suspendLaunch failed", e, "BaseViewModel")
                failed(scope, e.convert())
            } finally {
                complete()
            }
        }
    }


    class LoadStateWatcher(
        private val viewModel: BaseViewModel,
    ) {
        @Composable
        fun Watch(slot: LoadSlot, autoClear: Boolean = true, block: suspend (LoadState) -> Unit) {
            LaunchedEffect(slot) {
                slot.state
                    .mapNotNull { it }
                    .onEach {
                        logI("watch state = $it", tag = "LoadStateWatcher")
                        launch {
                            block(it)
                        }
                        if (autoClear) {
                            slot.clear()
                        }
                    }.launchIn(this)
            }
        }
    }

    @Composable
    fun LoadStateLaunchEffect(builder: @Composable LoadStateWatcher.() -> Unit) {
        val watcher = remember {
            LoadStateWatcher(this)
        }
        builder(watcher)
    }
}

class LoadSlot {
    private val _state = MutableStateFlow<BaseViewModel.LoadState?>(null)
    internal val state = _state.asStateFlow()

    internal fun setState(state: BaseViewModel.LoadState?) {
        _state.value = state
    }

    internal fun clear() {
        _state.value = null
    }
}
