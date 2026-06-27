package io.github.lumkit.tweak.sharednative

object GpuInfoBridge {

    init {
        System.loadLibrary("tweak_shared_native")
    }

    @JvmStatic
    external fun getGlesInfo(): String
}
