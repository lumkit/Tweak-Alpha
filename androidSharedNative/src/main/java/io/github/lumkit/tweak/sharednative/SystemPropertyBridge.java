package io.github.lumkit.tweak.sharednative;

public final class SystemPropertyBridge {

    static {
        System.loadLibrary("tweak_shared_native");
    }

    private SystemPropertyBridge() {
    }

    public static native String get(String name);
}
