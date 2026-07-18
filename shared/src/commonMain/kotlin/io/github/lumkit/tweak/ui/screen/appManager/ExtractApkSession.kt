package io.github.lumkit.tweak.ui.screen.appManager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExtractApkTask(
    val packageName: String,
    val appName: String,
)

/**
 * 安装包提取会话状态，供 [io.github.lumkit.tweak.service.ExtractApkService] 与 AppManager UI 共享。
 * 支持单任务与多选批量：一个 Dialog 同时展示当前任务进度与总进度。
 */
object ExtractApkSession {

    data class State(
        val running: Boolean = false,
        /** 进度弹窗是否显示；关闭后提取仍可在后台继续 */
        val dialogVisible: Boolean = false,
        val tasks: List<ExtractApkTask> = emptyList(),
        val currentIndex: Int = 0,
        val targetDir: String = "",
        val outputDir: String = "",
        val taskProgress: Int = 0,
        val overallProgress: Int = 0,
        val currentFile: String = "",
        val message: String = "",
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val snackbarMessage: String? = null,
    ) {
        val totalCount: Int get() = tasks.size

        val currentTask: ExtractApkTask? get() = tasks.getOrNull(currentIndex)

        val currentAppName: String
            get() = currentTask?.appName?.takeIf(String::isNotBlank)
                ?: currentTask?.packageName.orEmpty()

        val showDialog: Boolean get() = running && dialogVisible
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun begin(
        tasks: List<ExtractApkTask>,
        targetDir: String,
        outputDir: String,
        message: String,
    ) {
        _state.value = State(
            running = true,
            dialogVisible = true,
            tasks = tasks,
            currentIndex = 0,
            targetDir = targetDir,
            outputDir = outputDir,
            taskProgress = 0,
            overallProgress = 0,
            message = message,
        )
    }

    fun dismissDialog() {
        _state.update { it.copy(dialogVisible = false) }
    }

    fun startTask(index: Int) {
        _state.update { state ->
            val total = state.tasks.size
            state.copy(
                currentIndex = index.coerceIn(0, (total - 1).coerceAtLeast(0)),
                taskProgress = 0,
                currentFile = "",
                overallProgress = computeOverallProgress(index, 0, total),
            )
        }
    }

    fun updateTaskProgress(
        progress: Int,
        currentFile: String,
        message: String,
    ) {
        _state.update { state ->
            val taskProgress = progress.coerceIn(0, 100)
            state.copy(
                taskProgress = taskProgress,
                currentFile = currentFile,
                message = message,
                overallProgress = computeOverallProgress(
                    currentIndex = state.currentIndex,
                    taskProgress = taskProgress,
                    total = state.tasks.size,
                ),
            )
        }
    }

    fun completeTask(success: Boolean) {
        _state.update { state ->
            val nextIndex = state.currentIndex + 1
            state.copy(
                successCount = state.successCount + if (success) 1 else 0,
                failureCount = state.failureCount + if (success) 0 else 1,
                taskProgress = 100,
                overallProgress = computeOverallProgress(
                    currentIndex = nextIndex,
                    taskProgress = 0,
                    total = state.tasks.size,
                ),
            )
        }
    }

    fun finish(snackbarMessage: String) {
        _state.update {
            it.copy(
                running = false,
                dialogVisible = false,
                taskProgress = 100,
                overallProgress = 100,
                snackbarMessage = snackbarMessage,
            )
        }
    }

    fun consumeSnackbarMessage() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun isRunning(): Boolean = _state.value.running

    private fun computeOverallProgress(
        currentIndex: Int,
        taskProgress: Int,
        total: Int,
    ): Int {
        if (total <= 0) return 0
        if (currentIndex >= total) return 100
        val fraction = (currentIndex + taskProgress.coerceIn(0, 100) / 100.0) / total
        return (fraction * 100.0).toInt().coerceIn(0, 100)
    }
}
