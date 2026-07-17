package io.github.lumkit.tweak.sharednative

internal object NativeFileBridge {

    init {
        System.loadLibrary("tweak_shared_native")
    }

    @JvmStatic
    external fun exists(path: String): Boolean

    @JvmStatic
    external fun readBytes(path: String): ByteArray

    @JvmStatic
    external fun writeBytes(path: String, bytes: ByteArray)

    @JvmStatic
    external fun delete(path: String, recursive: Boolean)

    @JvmStatic
    external fun list(path: String): Array<String>?

    @JvmStatic
    external fun mkdirs(path: String)

    @JvmStatic
    external fun copy(sourcePath: String, targetPath: String, overwrite: Boolean)

    @JvmStatic
    external fun move(sourcePath: String, targetPath: String, overwrite: Boolean)

    @JvmStatic
    external fun chmod(path: String, mode: String)

    @JvmStatic
    external fun length(path: String): Long
}
