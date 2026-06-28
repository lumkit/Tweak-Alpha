package io.github.lumkit.tweak.sharednative

import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

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

    @JvmStatic
    fun unzipToDir(pfd: ParcelFileDescriptor, targetDir: String): Bundle = try {
        // 确保目标目录存在
        NativeFileBridge.mkdirs(targetDir)

        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            ZipInputStream(input).use { zipStream ->
                var entry = zipStream.nextEntry

                while (entry != null) {
                    val entryName = entry.name
                    val targetPath = File(targetDir, entryName).absolutePath

                    // 防止 Zip Slip 攻击
                    if (!targetPath.startsWith(targetDir)) {
                        throw SecurityException("Zip entry is outside target directory: $entryName")
                    }

                    if (entry.isDirectory) {
                        NativeFileBridge.mkdirs(targetPath)
                    } else {
                        // 确保父目录存在
                        val parentDir = File(targetPath).parent
                        if (parentDir != null) {
                            NativeFileBridge.mkdirs(parentDir)
                        }

                        // 流式写入文件，避免大文件 OOM
                        val targetFile = File(targetPath)
                        targetFile.outputStream().use { output ->
                            zipStream.copyTo(output, bufferSize = 8192)
                        }
                    }

                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }
        NativeFileBundles.successUnit()
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }
}
