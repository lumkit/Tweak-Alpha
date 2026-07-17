package io.github.lumkit.tweak.ui.screen.filePicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.lumkit.tweak.navigation.LocalNavigator
import io.github.lumkit.tweak.navigation.Screen
import io.github.lumkit.tweak.ui.screen.filePicker.FilePickerResultRegistry.pending
import io.github.lumkit.tweak.ui.screen.filePicker.FilePickerResultRegistry.unregister
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件选择器启动器。
 *
 * 通过 [launch] 传入 [FilePickerAction] 打开选择页面，确认后由
 * [rememberFilePickerLauncher] 注册的回调返回已选路径列表。
 */
class FilePickerLauncher internal constructor(
    private val onLaunch: (FilePickerAction) -> Unit,
) {
    fun launch(action: FilePickerAction) {
        onLaunch(action)
    }
}

/**
 * 创建并记住文件选择器启动器。
 *
 * 导航到选择页时调用方会离开 Composition，因此回调会在 [launch] 时标记为 pending，
 * 在结果送达或取消前不会随 [DisposableEffect] 的 onDispose 被清除。
 *
 * @param onResult 用户点击确认后回调已选择的绝对路径列表
 */
@Composable
fun rememberFilePickerLauncher(
    onResult: (List<String>) -> Unit,
): FilePickerLauncher {
    val navigator = LocalNavigator.current
    val requestId = remember { UUID.randomUUID().toString() }
    val onResultState = rememberUpdatedState(onResult)

    DisposableEffect(requestId) {
        FilePickerResultRegistry.register(requestId) { paths ->
            onResultState.value(paths)
        }
        onDispose {
            // 选择页仍在栈上时保留回调，避免 navigate 导致 onDispose 误清
            FilePickerResultRegistry.unregister(requestId)
        }
    }

    return remember(navigator, requestId, onResultState) {
        FilePickerLauncher { action ->
            FilePickerResultRegistry.register(requestId) { paths ->
                onResultState.value(paths)
            }
            FilePickerResultRegistry.markPending(requestId)
            navigator.navigate(
                Screen.FilePicker(
                    requestId = requestId,
                    action = action,
                )
            )
        }
    }
}

/**
 * 文件选择结果注册表。
 *
 * [pending] 表示对应 [requestId] 已打开选择页、等待确认或取消；
 * 在此期间 [unregister] 不会移除回调，以保证跨页面导航后仍可回传结果。
 */
internal object FilePickerResultRegistry {
    private val callbacks = ConcurrentHashMap<String, (List<String>) -> Unit>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    fun register(requestId: String, callback: (List<String>) -> Unit) {
        callbacks[requestId] = callback
    }

    fun markPending(requestId: String) {
        pending.add(requestId)
    }

    fun clearPending(requestId: String) {
        pending.remove(requestId)
    }

    fun unregister(requestId: String) {
        if (requestId in pending) return
        callbacks.remove(requestId)
    }

    fun deliver(requestId: String, paths: List<String>) {
        pending.remove(requestId)
        callbacks.remove(requestId)?.invoke(paths)
    }
}
