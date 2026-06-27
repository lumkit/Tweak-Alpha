package io.github.lumkit.tweak.sharednative

/**
 * 存储信息桥接类，通过 JNI 调用 C++ 层实现。
 */
object StorageBridge {

    init {
        System.loadLibrary("tweak_shared_native")
    }

    /**
     * 获取指定路径所在文件系统的总空间大小，单位字节。
     */
    @JvmStatic
    external fun getTotalBytes(path: String): Long

    /**
     * 获取指定路径所在文件系统的已使用空间大小，单位字节。
     */
    @JvmStatic
    external fun getUsedBytes(path: String): Long

    /**
     * 获取用户空间列表（/data/user/ 下的用户 ID）。
     */
    @JvmStatic
    external fun getUserProfiles(): Array<String>
}
