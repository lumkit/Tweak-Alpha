package io.github.lumkit.tweak.common.feature

import kotlinx.coroutines.CoroutineScope

expect fun commitInstallRom(path: String)

expect fun commitCancelUpdate()

expect fun commitMergeUpdate()

expect fun commitResetUpdate()

expect fun commitSuspendUpdate()

expect fun commitResumeUpdate()

expect fun CoroutineScope.setupUpdateForegroundService()