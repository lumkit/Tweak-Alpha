package io.github.lumkit.tweak.sharednative

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
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
    fun readCpuCycles(coreIndex: Int): Bundle = try {
        NativeFileBundles.successLong(CpuCyclesBridge.readCpuCycles(coreIndex))
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun listInstalledApps(): Bundle = try {
        val pm = PrivilegedContextProvider.requirePackageManagerContext().packageManager
        val matchFlags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        val packageInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(matchFlags))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(matchFlags.toInt())
        }
        val bundles = packageInfos.mapNotNull { it.toInstalledAppBundle(pm) }
        NativeFileBundles.successBundleList(bundles)
    } catch (throwable: Throwable) {
        NativeFileBundles.failure(throwable)
    }

    @JvmStatic
    fun getInstalledApp(packageName: String): Bundle = try {
        val pm = PrivilegedContextProvider.requirePackageManagerContext().packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        val bundle = packageInfo.toInstalledAppBundle(pm)
            ?: throw IllegalStateException("applicationInfo is null for $packageName")
        NativeFileBundles.successBundle(bundle)
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

private fun PackageInfo.toInstalledAppBundle(pm: PackageManager): Bundle? {
    val appInfo = applicationInfo ?: return null
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
    val state = resolveAppState(pm, packageName, appInfo)
    return Bundle().apply {
        putString(NativeFileBundles.KEY_PACKAGE_NAME, packageName)
        putString(NativeFileBundles.KEY_APP_NAME, runCatching {
            appInfo.loadLabel(pm).toString()
        }.getOrDefault(packageName))
        putString(NativeFileBundles.KEY_VERSION_NAME, versionName.orEmpty())
        putLong(NativeFileBundles.KEY_VERSION_CODE, versionCode)
        putInt(NativeFileBundles.KEY_UID, appInfo.uid)
        putString(NativeFileBundles.KEY_DATA_DIR, appInfo.dataDir.orEmpty())
        putString(NativeFileBundles.KEY_SOURCE_DIR, appInfo.sourceDir.orEmpty())
        putInt(
            NativeFileBundles.KEY_MIN_SDK,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0
        )
        putInt(NativeFileBundles.KEY_TARGET_SDK, appInfo.targetSdkVersion)
        putLong(NativeFileBundles.KEY_FIRST_INSTALL_TIME, firstInstallTime)
        putLong(NativeFileBundles.KEY_LAST_UPDATE_TIME, lastUpdateTime)
        putBoolean(
            NativeFileBundles.KEY_IS_SYSTEM_APP,
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        )
        putString(NativeFileBundles.KEY_APP_STATE, state)
    }
}

private fun resolveAppState(
    pm: PackageManager,
    packageName: String,
    appInfo: ApplicationInfo,
): String {
    val setting = runCatching { pm.getApplicationEnabledSetting(packageName) }
        .getOrNull() ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    return when (setting) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "FROZEN"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "DISABLED"
        else -> if (appInfo.enabled) "ENABLED" else "DISABLED"
    }
}
