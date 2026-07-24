package io.github.lumkit.tweak.common.feature

expect fun commitInstallRom(path: String)

expect fun commitCancelUpdate()

expect fun commitMergeUpdate()

expect fun commitResetUpdate()

expect fun commitSuspendUpdate()

expect fun commitResumeUpdate()

/**
 * 在 Root + 支持 OTA 时确保 UpdateEngineService 已启动。
 * 由无障碍拉活或用户进入系统更新页时调用。
 */
expect suspend fun ensureUpdateEngineService()
