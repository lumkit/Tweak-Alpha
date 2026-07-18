package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.service.ExtractApkService
import io.github.lumkit.tweak.ui.screen.appManager.ExtractApkTask

actual fun commitStartExtractApk(
    tasks: List<ExtractApkTask>,
    targetDir: String,
) {
    ExtractApkService.start(
        tasks = tasks,
        targetDir = targetDir,
    )
}
