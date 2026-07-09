package io.github.lumkit.tweak.sharednative

internal object CpuCyclesBridge {

    init {
        System.loadLibrary("tweak_shared_native")
    }

    @JvmStatic
    external fun readCpuCycles(coreIndex: Int): Long
}
