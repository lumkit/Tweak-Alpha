package io.github.lumkit.tweak.model

import androidx.compose.runtime.Composable
import io.github.lumkit.tweak.common.utils.NativeFileBackend
import org.jetbrains.compose.resources.stringResource
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_root_mode
import tweak_alpha.shared.generated.resources.text_root_mode_description
import tweak_alpha.shared.generated.resources.text_shizuku_mode
import tweak_alpha.shared.generated.resources.text_shizuku_mode_description

/**
 * 运行模式
 */
enum class RuntimeMode(val user: Int) {
    /**
     * 未知模式，一般不予进入App
     */
    Unknow(Int.MAX_VALUE),

    /**
     * Root模式
     */
    Root(0),

    /**
     * Shizuku模式
     */
    Shizuku(1)
}

@Composable
fun stringResourceByRuntimeMode(runtimeMode: RuntimeMode): String {
    return when (runtimeMode) {
        RuntimeMode.Unknow -> ""
        RuntimeMode.Root -> stringResource(Res.string.text_root_mode)
        RuntimeMode.Shizuku -> stringResource(Res.string.text_shizuku_mode)
    }
}

@Composable
fun stringResourceByRuntimeModeDescription(runtimeMode: RuntimeMode): String {
    return when (runtimeMode) {
        RuntimeMode.Unknow -> ""
        RuntimeMode.Root -> stringResource(Res.string.text_root_mode_description)
        RuntimeMode.Shizuku -> stringResource(Res.string.text_shizuku_mode_description)
    }
}

fun RuntimeMode.asNativeFileBackend(): NativeFileBackend = when (this) {
    RuntimeMode.Unknow -> NativeFileBackend.User
    RuntimeMode.Root -> NativeFileBackend.ROOT
    RuntimeMode.Shizuku -> NativeFileBackend.SHIZUKU
}

fun selectNativeFileBackend(mode: RuntimeMode?): NativeFileBackend {
    return (mode ?: RuntimeMode.Unknow).asNativeFileBackend()
}
