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

    /** 未捕获异常崩溃后展示；禁止 adb / 通用深链唤起。 */
    @Serializable
    data object Crash : Screen()

    @Serializable
    data object UpdateSystem: Screen()

    @Serializable
    data object FpsRecord: Screen()

    @Serializable
    data class FpsRecordDetail(val id: Long): Screen()

    @Serializable
    data class FpsRecordThreads(val id: Long): Screen()

    @Serializable
    data object OpenSources: Screen()

    @Serializable
    data object AppManager: Screen()

    /**
     * 进程管理。
     *
     * @param scrollToPackage 进入后滚动定位的包名（可为空）
     * @param scrollToPid 进入后优先按 PID 定位（-1 表示忽略）
     */
    @Serializable
    data class ProcessManager(
        val scrollToPackage: String = "",
        val scrollToPid: Int = -1,
    ) : Screen()

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
