package io.github.lumkit.tweak.sharednative

object SystemPropertyBridge {

    init {
        System.loadLibrary("tweak_shared_native")
    }

    @JvmStatic
    external fun get(name: String): String
}
