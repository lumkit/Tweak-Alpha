package io.github.lumkit.tweak.sharednative

import android.os.Bundle
import java.nio.charset.StandardCharsets

/**
 * 文件服务委托，封装所有文件操作的公共逻辑。
 * 由 RootFileService 和 ShizukuFileService 共享调用。
 */
internal object FileServiceDelegate {

    @JvmStatic
    fun exists(path: String): Bundle = try {
        NativeFileBundles.successBoolean(NativeFileBridge.exists(path))
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun list(path: String): Bundle = try {
        val entries = NativeFileBridge.list(path)
        NativeFileBundles.successStringList(entries?.toList() ?: emptyList())
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun zipEntries(path: String): Bundle = try {
        val entries = ZipEntryReader.readEntries(path)
        NativeFileBundles.successBundleList(entries)
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun readBytes(path: String): Bundle = try {
        NativeFileBundles.successBytes(NativeFileBridge.readBytes(path))
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun readText(path: String): Bundle = try {
        val bytes = NativeFileBridge.readBytes(path)
        NativeFileBundles.successString(String(bytes, StandardCharsets.UTF_8))
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun writeBytes(path: String, bytes: ByteArray): Bundle = try {
        NativeFileBridge.writeBytes(path, bytes)
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun writeText(path: String, text: String): Bundle = try {
        NativeFileBridge.writeBytes(path, text.toByteArray(StandardCharsets.UTF_8))
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun delete(path: String, recursive: Boolean): Bundle = try {
        NativeFileBridge.delete(path, recursive)
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun mkdirs(path: String): Bundle = try {
        NativeFileBridge.mkdirs(path)
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun copy(sourcePath: String, targetPath: String, overwrite: Boolean): Bundle = try {
        NativeFileBridge.copy(sourcePath, targetPath, overwrite)
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun move(sourcePath: String, targetPath: String, overwrite: Boolean): Bundle = try {
        NativeFileBridge.move(sourcePath, targetPath, overwrite)
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun chmod(path: String, mode: String): Bundle = try {
        NativeFileBridge.chmod(path, mode)
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }
}
