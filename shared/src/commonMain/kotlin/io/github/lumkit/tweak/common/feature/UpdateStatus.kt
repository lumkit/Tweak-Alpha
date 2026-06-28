package io.github.lumkit.tweak.common.feature

/**
 * Android update_engine 事件封装。
 *
 * 包含两类事件：
 * - [StatusUpdate]：状态变化回调 onStatusUpdate(status, progress)
 * - [PayloadComplete]：更新完成回调 onPayloadApplicationComplete(errorCode)
 */
sealed interface UpdateEngineEvent {

    /**
     * 状态变化事件，对应 onStatusUpdate 回调。
     */
    data class StatusUpdate(val status: UpdateStatus) : UpdateEngineEvent

    /**
     * 更新完成事件，对应 onPayloadApplicationComplete 回调。
     */
    data class PayloadComplete(val errorCode: UpdateErrorCode) : UpdateEngineEvent

    /**
     * update_engine_client 命令耗时信息。
     */
    data class CommandTook(val millis: Long) : UpdateEngineEvent

    companion object {
        private val STATUS_REGEX = Regex(
            """\bonStatusUpdate\((\w+)\s*\((\d+)\),\s*([\d.eE+\-]+)\)"""
        )
        private val COMPLETE_REGEX = Regex(
            """\bonPayloadApplicationComplete\((\w+)\s*\((\d+)\)\)"""
        )
        private val COMMAND_TOOK_REGEX = Regex(
            """Command took (\d+) ms"""
        )

        /**
         * 从 update_engine 日志行中解析出 [UpdateEngineEvent]。
         *
         * @param line 日志原始行
         * @return 解析成功返回对应事件，无法匹配返回 null
         */
        fun parse(line: String): UpdateEngineEvent? {
            // 尝试解析状态更新
            STATUS_REGEX.find(line)?.let { match ->
                val statusCode = match.groupValues[2].toIntOrNull() ?: return null
                val progress = match.groupValues[3].toFloatOrNull() ?: 0f
                return StatusUpdate(UpdateStatus.fromCode(statusCode, progress))
            }

            // 尝试解析完成回调
            COMPLETE_REGEX.find(line)?.let { match ->
                val errorCode = match.groupValues[2].toIntOrNull() ?: return null
                return PayloadComplete(UpdateErrorCode.fromCode(errorCode))
            }

            // 尝试解析命令耗时
            COMMAND_TOOK_REGEX.find(line)?.let { match ->
                val millis = match.groupValues[1].toLongOrNull() ?: return null
                return CommandTook(millis)
            }

            return null
        }
    }
}

/**
 * Android update_engine 状态封装。
 *
 * 对应 AOSP system/update_engine 中的 UpdateStatus 枚举。
 * 每种状态携带当前进度 [progress]（0.0 ~ 1.0）。
 */
sealed class UpdateStatus(val code: Int, val progress: Float) {

    /** 空闲，无更新任务 */
    data class Idle(val p: Float = 0f) : UpdateStatus(CODE_IDLE, p)

    /** 正在检查是否有可用更新 */
    data class CheckingForUpdate(val p: Float = 0f) : UpdateStatus(CODE_CHECKING_FOR_UPDATE, p)

    /** 检测到可用更新 */
    data class UpdateAvailable(val p: Float = 0f) : UpdateStatus(CODE_UPDATE_AVAILABLE, p)

    /** 正在下载更新 payload */
    data class Downloading(val p: Float = 0f) : UpdateStatus(CODE_DOWNLOADING, p)

    /** 正在校验已下载的 payload */
    data class Verifying(val p: Float = 0f) : UpdateStatus(CODE_VERIFYING, p)

    /** 正在执行 post-install 脚本并完成更新 */
    data class Finalizing(val p: Float = 0f) : UpdateStatus(CODE_FINALIZING, p)

    /** 更新已完成，需要重启以切换到新槽位 */
    data class UpdatedNeedReboot(val p: Float = 0f) : UpdateStatus(CODE_UPDATED_NEED_REBOOT, p)

    /** 更新过程中发生错误，正在上报错误事件 */
    data class ReportingErrorEvent(val p: Float = 0f) : UpdateStatus(CODE_REPORTING_ERROR_EVENT, p)

    /** 正在尝试回滚到上一个版本 */
    data class AttemptingRollback(val p: Float = 0f) : UpdateStatus(CODE_ATTEMPTING_ROLLBACK, p)

    /** 更新功能已禁用 */
    data class Disabled(val p: Float = 0f) : UpdateStatus(CODE_DISABLED, p)

    /** 正在清理上一次更新的残留数据 */
    data class CleanupPreviousUpdate(val p: Float = 0f) : UpdateStatus(CODE_CLEANUP_PREVIOUS_UPDATE, p)

    /** 未知状态（用于容错） */
    data class Unknown(val statusCode: Int, val p: Float = 0f) : UpdateStatus(statusCode, p)

    companion object {
        const val CODE_IDLE = 0
        const val CODE_CHECKING_FOR_UPDATE = 1
        const val CODE_UPDATE_AVAILABLE = 2
        const val CODE_DOWNLOADING = 3
        const val CODE_VERIFYING = 4
        const val CODE_FINALIZING = 5
        const val CODE_UPDATED_NEED_REBOOT = 6
        const val CODE_REPORTING_ERROR_EVENT = 7
        const val CODE_ATTEMPTING_ROLLBACK = 8
        const val CODE_DISABLED = 9
        // 10 = NEED_PERMISSION_TO_UPDATE (ChromeOS only, Android 不使用)
        const val CODE_CLEANUP_PREVIOUS_UPDATE = 11

        /**
         * 根据状态码和进度值创建对应的 [UpdateStatus] 实例。
         */
        fun fromCode(code: Int, progress: Float = 0f): UpdateStatus {
            return when (code) {
                CODE_IDLE -> Idle(progress)
                CODE_CHECKING_FOR_UPDATE -> CheckingForUpdate(progress)
                CODE_UPDATE_AVAILABLE -> UpdateAvailable(progress)
                CODE_DOWNLOADING -> Downloading(progress)
                CODE_VERIFYING -> Verifying(progress)
                CODE_FINALIZING -> Finalizing(progress)
                CODE_UPDATED_NEED_REBOOT -> UpdatedNeedReboot(progress)
                CODE_REPORTING_ERROR_EVENT -> ReportingErrorEvent(progress)
                CODE_ATTEMPTING_ROLLBACK -> AttemptingRollback(progress)
                CODE_DISABLED -> Disabled(progress)
                CODE_CLEANUP_PREVIOUS_UPDATE -> CleanupPreviousUpdate(progress)
                else -> Unknown(code, progress)
            }
        }
    }
}

expect fun UpdateStatus.asMsg(): String

/**
 * update_engine 错误码封装。
 *
 * 对应 AOSP system/update_engine/common/error_code.h
 */
sealed class UpdateErrorCode(val code: Int) {

    /** 操作成功 */
    data object Success : UpdateErrorCode(0)

    /** 通用错误 */
    data object Error : UpdateErrorCode(1)

    /** 文件系统复制错误 */
    data object FilesystemCopierError : UpdateErrorCode(4)

    /** post-install 脚本执行失败 */
    data object PostInstallRunnerError : UpdateErrorCode(5)

    /** payload 类型不匹配（如使用了不支持的特性） */
    data object PayloadMismatchedTypeError : UpdateErrorCode(6)

    /** 无法打开安装目标设备（分区） */
    data object InstallDeviceOpenError : UpdateErrorCode(7)

    /** 无法打开内核设备（分区） */
    data object KernelDeviceOpenError : UpdateErrorCode(8)

    /** 下载传输过程中出错 */
    data object DownloadTransferError : UpdateErrorCode(9)

    /** payload 哈希校验不匹配 */
    data object PayloadHashMismatchError : UpdateErrorCode(10)

    /** payload 大小与预期不匹配 */
    data object PayloadSizeMismatchError : UpdateErrorCode(11)

    /** 下载的 payload 验证失败 */
    data object DownloadPayloadVerificationError : UpdateErrorCode(12)

    /** 下载状态初始化失败（如源分区哈希不匹配） */
    data object DownloadStateInitializationError : UpdateErrorCode(20)

    /** 未知错误码 */
    data class Unknown(val errorCode: Int) : UpdateErrorCode(errorCode)

    /** 是否表示成功 */
    val isSuccess: Boolean get() = this is Success

    /** 是否表示失败 */
    val isError: Boolean get() = !isSuccess

    companion object {
        fun fromCode(code: Int): UpdateErrorCode {
            return when (code) {
                0 -> Success
                1 -> Error
                4 -> FilesystemCopierError
                5 -> PostInstallRunnerError
                6 -> PayloadMismatchedTypeError
                7 -> InstallDeviceOpenError
                8 -> KernelDeviceOpenError
                9 -> DownloadTransferError
                10 -> PayloadHashMismatchError
                11 -> PayloadSizeMismatchError
                12 -> DownloadPayloadVerificationError
                20 -> DownloadStateInitializationError
                else -> Unknown(code)
            }
        }
    }
}

expect fun UpdateErrorCode.asMsg(): String