package io.github.lumkit.tweak.navigation

import androidx.navigation3.runtime.NavKey
import io.github.lumkit.tweak.ui.screen.filePicker.FilePickerAction
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen: NavKey {

    @Serializable
    data object Splash: Screen()

    @Serializable
    data object Main: Screen()

    @Serializable
    data object UpdateSystem: Screen()

    @Serializable
    data object FpsRecord: Screen()

    @Serializable
    data class FpsRecordDetail(val id: Long): Screen()

    @Serializable
    data object OpenSources: Screen()

    @Serializable
    data object AppManager: Screen()

    @Serializable
    data object FlashRom : Screen()

    /**
     * 文件选择器。
     *
     * @param requestId 与 [io.github.lumkit.tweak.ui.screen.filePicker.rememberFilePickerLauncher] 对应
     * @param action 选择类型
     */
    @Serializable
    data class FilePicker(
        val requestId: String,
        val action: FilePickerAction,
    ) : Screen()
}
