package io.github.lumkit.tweak.ui.screen.fpsRecord

import io.github.lumkit.tweak.common.utils.canDrawOverlays
import io.github.lumkit.tweak.service.FpsRecordService

actual fun showRecordOverlay() {
    if (canDrawOverlays()) {
        FpsRecordService.showRecordOverlay()
    }
}

actual fun hideRecordOverlay() {
    FpsRecordService.hideRecordOverlay()
}