package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.shared.R
import kotlin.math.roundToInt

actual fun UpdateStatus.asMsg(): String {
    val percent = (this.progress.coerceIn(0f, 1f) * 100).roundToInt()

    return when (this) {
        is UpdateStatus.Idle -> application.getString(R.string.update_status_idle)
        is UpdateStatus.CheckingForUpdate -> application.getString(R.string.update_status_checking_for_update)
        is UpdateStatus.UpdateAvailable -> application.getString(R.string.update_status_update_available)
        is UpdateStatus.Downloading -> application.getString(R.string.update_status_downloading, percent)
        is UpdateStatus.Verifying -> application.getString(R.string.update_status_verifying, percent)
        is UpdateStatus.Finalizing -> application.getString(R.string.update_status_finalizing)
        is UpdateStatus.UpdatedNeedReboot -> application.getString(R.string.update_status_updated_need_reboot)
        is UpdateStatus.ReportingErrorEvent -> application.getString(R.string.update_status_reporting_error_event)
        is UpdateStatus.AttemptingRollback -> application.getString(R.string.update_status_attempting_rollback)
        is UpdateStatus.Disabled -> application.getString(R.string.update_status_disabled)
        is UpdateStatus.CleanupPreviousUpdate -> application.getString(R.string.update_status_cleanup_previous_update)
        is UpdateStatus.Unknown -> application.getString(R.string.update_status_unknown, this.code)
    }
}

actual fun UpdateErrorCode.asMsg(): String = when (val errorCode = this) {
    UpdateErrorCode.Success -> application.getString(R.string.update_error_success)
    UpdateErrorCode.Error -> application.getString(R.string.update_error_generic)
    UpdateErrorCode.FilesystemCopierError -> application.getString(R.string.update_error_filesystem_copier)
    UpdateErrorCode.PostInstallRunnerError -> application.getString(R.string.update_error_post_install_runner)
    UpdateErrorCode.PayloadMismatchedTypeError -> application.getString(R.string.update_error_payload_mismatched_type)
    UpdateErrorCode.InstallDeviceOpenError -> application.getString(R.string.update_error_install_device_open)
    UpdateErrorCode.KernelDeviceOpenError -> application.getString(R.string.update_error_kernel_device_open)
    UpdateErrorCode.DownloadTransferError -> application.getString(R.string.update_error_download_transfer)
    UpdateErrorCode.PayloadHashMismatchError -> application.getString(R.string.update_error_payload_hash_mismatch)
    UpdateErrorCode.PayloadSizeMismatchError -> application.getString(R.string.update_error_payload_size_mismatch)
    UpdateErrorCode.DownloadPayloadVerificationError -> application.getString(R.string.update_error_download_payload_verification)
    UpdateErrorCode.DownloadStateInitializationError -> application.getString(R.string.update_error_download_state_initialization)
    is UpdateErrorCode.Unknown -> application.getString(R.string.update_error_unknown, errorCode.code)
}