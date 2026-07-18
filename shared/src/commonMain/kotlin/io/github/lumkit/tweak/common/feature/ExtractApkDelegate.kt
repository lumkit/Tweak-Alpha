package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.ui.screen.appManager.ExtractApkTask

expect fun commitStartExtractApk(
    tasks: List<ExtractApkTask>,
    targetDir: String,
)
