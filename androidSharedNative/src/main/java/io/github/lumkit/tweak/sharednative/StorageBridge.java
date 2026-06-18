package io.github.lumkit.tweak.sharednative;

/**
 * 存储信息桥接类，通过 JNI 调用 C++ 层实现。
 */
public final class StorageBridge {

    static {
        System.loadLibrary("tweak_shared_native");
    }

    private StorageBridge() {
    }

    /**
     * 获取指定路径所在文件系统的总空间大小，单位字节。
     */
    public static native long getTotalBytes(String path);

    /**
     * 获取指定路径所在文件系统的已使用空间大小，单位字节。
     */
    public static native long getUsedBytes(String path);

    /**
     * 获取用户空间列表（/data/user/ 下的用户 ID）。
     */
    public static native String[] getUserProfiles();
}
