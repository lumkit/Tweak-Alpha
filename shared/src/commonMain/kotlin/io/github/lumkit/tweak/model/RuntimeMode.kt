package io.github.lumkit.tweak.model

import androidx.compose.runtime.Composable
import io.github.lumkit.tweak.common.utils.NativeFileBackend
import org.jetbrains.compose.resources.stringResource
import tweak.shared.generated.resources.Res
import tweak.shared.generated.resources.text_root_mode
import tweak.shared.generated.resources.text_root_mode_description

/**
 * 运行模式
 */
enum class RuntimeMode {
    /**
     * 未知模式，一般不予进入App
     */
    Unknow,

    /**
     * Root模式
     */
    Root
}

@Composable
fun stringResourceByRuntimeMode(runtimeMode: RuntimeMode): String {
    return when (runtimeMode) {
        RuntimeMode.Unknow -> ""
        RuntimeMode.Root -> stringResource(Res.string.text_root_mode)
    }
}

@Composable
fun stringResourceByRuntimeModeDescription(runtimeMode: RuntimeMode): String {
    return when (runtimeMode) {
        RuntimeMode.Unknow -> ""
        RuntimeMode.Root -> stringResource(Res.string.text_root_mode_description)
    }
}

fun RuntimeMode.asNativeFileBackend(): NativeFileBackend = when (this) {
    RuntimeMode.Unknow -> NativeFileBackend.User
    RuntimeMode.Root -> NativeFileBackend.ROOT
}