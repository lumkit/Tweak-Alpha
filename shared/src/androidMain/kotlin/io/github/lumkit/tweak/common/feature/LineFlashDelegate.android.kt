package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.service.LineFlashService

actual fun commitStartLineFlash() {
    LineFlashService.start()
}

actual fun commitCancelLineFlash(deviceLost: Boolean) {
    LineFlashService.cancel(deviceLost)
}
