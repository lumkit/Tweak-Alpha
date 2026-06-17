package io.github.lumkit.tweak.sharednative;

public final class GpuInfoBridge {

    static {
        System.loadLibrary("tweak_shared_native");
    }

    private GpuInfoBridge() {
    }

    public static native String getGlesInfo();
}
