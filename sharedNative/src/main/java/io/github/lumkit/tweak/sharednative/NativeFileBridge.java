package io.github.lumkit.tweak.sharednative;

final class NativeFileBridge {

    static {
        System.loadLibrary("tweak_shared_native");
    }

    private NativeFileBridge() {
    }

    static native boolean exists(String path);

    static native byte[] readBytes(String path);

    static native void writeBytes(String path, byte[] bytes);

    static native void delete(String path, boolean recursive);

    static native String[] list(String path);

    static native void mkdirs(String path);

    static native void copy(String sourcePath, String targetPath, boolean overwrite);

    static native void move(String sourcePath, String targetPath, boolean overwrite);

    static native void chmod(String path, String mode);
}
