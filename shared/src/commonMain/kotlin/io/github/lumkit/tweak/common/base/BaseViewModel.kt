package io.github.lumkit.tweak.common.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.utils.convert
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.logI
import io.ktor.util.collections.ConcurrentMap
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
    sealed class LoadState(open val id: String, open val message: String? = null) {

        data class Loading(
            override val id: String,
            override val message: String? = null
        ) : LoadState(id, message)

        data class Success(
            override val id: String,
            override val message: String? = null
        ) : LoadState(id, message)

        data class Failure(
            override val id: String,
            override val message: String? = null
        ) : LoadState(id, message)
    }

    interface LoadStateCoroutineScope {
        val id: Any?
        suspend fun loading(message: String? = null)
        suspend fun success(message: String? = null)
        suspend fun failure(throwable: Throwable? = null)
    }

    private val _loadStatePool = MutableStateFlow(ConcurrentMap<Any?, LoadState>())
    val loadState = _loadStatePool.asStateFlow()

    fun LoadStateCoroutineScope.updateLoadState(state: LoadState) {
        val copy = ConcurrentMap<Any?, LoadState>()
        copy.putAll(_loadStatePool.value)
        copy[id] = state
        _loadStatePool.value = copy
    }

    fun clearLoadState(id: Any?) {
        val copy = ConcurrentMap<Any?, LoadState>()
        copy.putAll(_loadStatePool.value)
        copy.remove(id)
        _loadStatePool.value = copy
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
     * @param id 唯一标识
     * @param failed 失败回调
     * @param complete 完成回调
     * @param block 代码块
     */
    fun suspendLaunch(
        id: Any?,
        context: CoroutineContext = Dispatchers.Main,
        failed: suspend LoadStateCoroutineScope.(Throwable) -> Unit = { failure(it) },
        complete: suspend () -> Unit = {},
        block: suspend LoadStateCoroutineScope.() -> Unit,
    ) {
        val scope = object : LoadStateCoroutineScope {
            override val id: Any?
                get() = id

            override suspend fun loading(message: String?) {
                updateLoadState(LoadState.Loading(id = id.toString(), message = message))
            }

            override suspend fun success(message: String?) {
                updateLoadState(LoadState.Success(id = id.toString(), message = message))
            }

            override suspend fun failure(throwable: Throwable?) {
                updateLoadState(LoadState.Failure(id = id.toString(), message = throwable?.message))
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
        private val state: State<ConcurrentMap<Any?, LoadState>>,
    ) {
        @Composable
        fun Watch(id: Any?, autoClear: Boolean = true, block: suspend (LoadState) -> Unit) {
            LaunchedEffect(this) {
                snapshotFlow { state.value }
                    .mapNotNull { it[id] }
                    .onEach {
                        logI("watch $id, state = $it", tag = "LoadStateWatcher")
                        launch {
                            block(it)
                        }
                        if (autoClear) {
                            viewModel.clearLoadState(id)
                        }
                    }.launchIn(this)
            }
        }
    }

    @Composable
    fun LoadStateLaunchEffect(builder: @Composable LoadStateWatcher.() -> Unit) {
        val state = loadState.collectAsStateWithLifecycle()
        val watcher = remember {
            LoadStateWatcher(this, state)
        }
        builder(watcher)
    }
}