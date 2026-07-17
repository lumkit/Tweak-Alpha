package io.github.lumkit.tweak.sharednative

import android.os.Bundle
import android.os.ParcelFileDescriptor

/**
 * Shizuku UserService 实现。
 * 此类运行在 Shizuku 提供的特权进程中，具有 shell (adb) 权限。
 */
class ShizukuFileService : IRootFileService.Stub() {

    override fun exists(path: String): Bundle {
        return FileServiceDelegate.exists(path)
    }

    override fun list(path: String): Bundle {
        return FileServiceDelegate.list(path)
    }

    override fun listEntries(path: String): Bundle {
        return FileServiceDelegate.listEntries(path)
    }

    override fun zipEntries(path: String): Bundle {
        return FileServiceDelegate.zipEntries(path)
    }

    override fun openReadOnlyFd(path: String): ParcelFileDescriptor {
        return FileServiceDelegate.openReadOnlyFd(path)
    }

    override fun openWriteOnlyFd(path: String, create: Boolean, truncate: Boolean): ParcelFileDescriptor {
        return FileServiceDelegate.openWriteOnlyFd(path, create, truncate)
    }

    override fun readBytes(path: String): Bundle {
        return FileServiceDelegate.readBytes(path)
    }

    override fun readText(path: String): Bundle {
        return FileServiceDelegate.readText(path)
    }

    override fun writeBytes(path: String, bytes: ByteArray): Bundle {
        return FileServiceDelegate.writeBytes(path, bytes)
    }

    override fun writeText(path: String, text: String): Bundle {
        return FileServiceDelegate.writeText(path, text)
    }

    override fun delete(path: String, recursive: Boolean): Bundle {
        return FileServiceDelegate.delete(path, recursive)
    }

    override fun mkdirs(path: String): Bundle {
        return FileServiceDelegate.mkdirs(path)
    }

    override fun copy(sourcePath: String, targetPath: String, overwrite: Boolean): Bundle {
        return FileServiceDelegate.copy(sourcePath, targetPath, overwrite)
    }

    override fun move(sourcePath: String, targetPath: String, overwrite: Boolean): Bundle {
        return FileServiceDelegate.move(sourcePath, targetPath, overwrite)
    }

    override fun chmod(path: String, mode: String): Bundle {
        return FileServiceDelegate.chmod(path, mode)
    }

    override fun length(path: String): Bundle {
        return FileServiceDelegate.length(path)
    }

    override fun readCpuCycles(coreIndex: Int): Bundle {
        return FileServiceDelegate.readCpuCycles(coreIndex)
    }

    override fun listInstalledApps(): Bundle {
        return FileServiceDelegate.listInstalledApps()
    }

    override fun getInstalledApp(packageName: String): Bundle {
        return FileServiceDelegate.getInstalledApp(packageName)
    }

    override fun unzipToDir(pfd: ParcelFileDescriptor, targetDir: String): Bundle {
        return FileServiceDelegate.unzipToDir(pfd, targetDir)
    }

    override fun unzipPathToDir(sourcePath: String, targetDir: String): Bundle {
        return FileServiceDelegate.unzipPathToDir(sourcePath, targetDir)
    }
}
