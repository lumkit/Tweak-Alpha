package io.github.lumkit.tweak.sharednative

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

class RootFileService : RootService() {

    private val binder = object : IRootFileService.Stub() {
        override fun exists(path: String): Bundle {
            return FileServiceDelegate.exists(path)
        }

        override fun list(path: String): Bundle {
            return FileServiceDelegate.list(path)
        }

        override fun zipEntries(path: String): Bundle {
            return FileServiceDelegate.zipEntries(path)
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
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
