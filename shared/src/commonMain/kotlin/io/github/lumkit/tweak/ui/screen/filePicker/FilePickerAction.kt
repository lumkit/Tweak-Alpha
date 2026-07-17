package io.github.lumkit.tweak.ui.screen.filePicker

import kotlinx.serialization.Serializable

/**
 * 文件选择器目标类型。
 */
@Serializable
enum class FilePickerAction {
    /** 单选文件 */
    File,

    /** 多选文件 */
    Files,

    /** 单选文件夹 */
    Folder,

    /** 多选文件夹 */
    Folders,
}

val FilePickerAction.isMultiSelect: Boolean
    get() = this == FilePickerAction.Files || this == FilePickerAction.Folders

val FilePickerAction.selectsDirectory: Boolean
    get() = this == FilePickerAction.Folder || this == FilePickerAction.Folders
