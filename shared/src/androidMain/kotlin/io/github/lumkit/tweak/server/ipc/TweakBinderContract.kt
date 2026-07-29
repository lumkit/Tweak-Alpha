package io.github.lumkit.tweak.server.ipc

object TweakBinderContract {
    const val METHOD_SET_BINDER = "setBinder"
    const val EXTRA_BINDER = "binder"

    fun authority(packageName: String): String = "$packageName.tweak.binder"
}
