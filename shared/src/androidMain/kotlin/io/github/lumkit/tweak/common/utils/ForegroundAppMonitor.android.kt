package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.service.TweakAccessibilityService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object ForegroundAppMonitor {

    actual val foregroundPackage: StateFlow<String?> =
        TweakAccessibilityService._foregroundPackage.asStateFlow()

    actual val isRunning: StateFlow<Boolean> =
        TweakAccessibilityService._isRunning.asStateFlow()

    actual val currentForegroundPackage: String?
        get() = TweakAccessibilityService._foregroundPackage.value
}
