package io.github.lumkit.tweak.common.utils

import android.os.Build
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.shell.ShellExecutor
import io.github.lumkit.tweak.common.utils.ToolkitInstaller.READY_MARK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 对齐 Scene/vtools：
 * - toybox-outside(64) → files/toolkit/
 * - busybox → 系统无 busybox 时解压到 files/toolkit/ 并 ln applet
 *
 * 快路径只做本地文件判断，不跑 shell，避免拖慢启动。
 * 注意：root 创建的 symlink 在 Java [File.exists] 上可能为 false，故用应用 UID
 * 写入的普通文件 [READY_MARK] 作为「已安装」标记。
 */
actual object ToolkitInstaller {

    private const val TAG = "ToolkitInstaller"
    private const val ASSET_DIR = "toolkit"
    private const val INSTALL_DIR = "toolkit"
    private const val READY_MARK = "busybox_installed"
    private const val LEGACY_SYMLINK_MARK = "busybox_1_30_1"
    /** 安装完成后 toolkit 目录 applet 数量通常远大于此 */
    private const val MIN_APPLET_ENTRIES = 20

    private val mutex = Mutex()

    @Volatile
    private var toyboxPath: String = ""

    @Volatile
    private var busyboxReady: Boolean? = null

    @Volatile
    private var toolkitPathInjected: Boolean = false

    actual suspend fun ensureToybox(): String = mutex.withLock {
        if (toyboxPath.isNotBlank() && isUsableBinary(File(toyboxPath))) {
            return toyboxPath
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val dest = resolveToyboxFile()
                if (isUsableBinary(dest)) {
                    dest.setExecutable(true, false)
                    toyboxPath = dest.absolutePath
                    logD("toybox already installed: $toyboxPath", TAG)
                    return@runCatching toyboxPath
                }
                copyAsset("$ASSET_DIR/${dest.name}", "$INSTALL_DIR/${dest.name}")
                dest.setExecutable(true, false)
                if (!isUsableBinary(dest)) {
                    error("toybox missing after copy: ${dest.absolutePath}")
                }
                toyboxPath = dest.absolutePath
                logD("toybox installed: $toyboxPath", TAG)
                toyboxPath
            }.onFailure {
                logE("ensureToybox failed: ${it.message}", it, TAG)
                toyboxPath = ""
            }.getOrDefault("")
        }
    }

    actual suspend fun ensureBusybox(): Boolean = mutex.withLock {
        busyboxReady?.let { return it }
        withContext(Dispatchers.IO) {
            val ok = runCatching {
                if (systemBusyboxInstalled()) {
                    logD("system busybox present, skip private install", TAG)
                    true
                } else {
                    ensurePrivateBusybox()
                }
            }.onFailure {
                logE("ensureBusybox failed: ${it.message}", it, TAG)
            }.getOrDefault(false)
            busyboxReady = ok
            ok
        }
    }

    private fun resolveToyboxFile(): File {
        val abi = Build.SUPPORTED_ABIS.orEmpty().joinToString(" ").lowercase(Locale.US)
        val fileName = if (abi.contains("arm64")) "toybox-outside64" else "toybox-outside"
        return privateFile("$INSTALL_DIR/$fileName")
    }

    private fun systemBusyboxInstalled(): Boolean {
        val candidates = listOf(
            "/sbin/busybox",
            "/system/xbin/busybox",
            "/system/sbin/busybox",
            "/system/bin/busybox",
            "/vendor/bin/busybox",
            "/vendor/xbin/busybox",
            "/odm/bin/busybox",
        )
        if (candidates.any { File(it).exists() }) {
            return true
        }
        return runCatching {
            Runtime.getRuntime().exec(arrayOf("busybox", "--help")).destroy()
            true
        }.getOrDefault(false)
    }

    private suspend fun ensurePrivateBusybox(): Boolean {
        val abi = Build.SUPPORTED_ABIS.orEmpty().joinToString(" ").lowercase(Locale.US)
        if (!abi.contains("arm")) {
            logE("busybox private install unsupported abi=$abi", null, TAG)
            return false
        }

        val dir = toolkitDir()
        val busyboxFile = privateFile("$INSTALL_DIR/busybox")

        if (isPrivateBusyboxReady(dir, busyboxFile)) {
            injectToolkitPath(dir)
            logD("private busybox already ready, skip copy/install", TAG)
            return true
        }

        dir.mkdirs()

        if (!isUsableBinary(busyboxFile)) {
            copyAsset("$ASSET_DIR/busybox", "$INSTALL_DIR/busybox")
            busyboxFile.setExecutable(true, false)
        }
        if (!isUsableBinary(busyboxFile)) {
            logE("busybox asset copy failed", null, TAG)
            return false
        }

        val rootBusybox = privateFile("busybox")
        if (!isUsableBinary(rootBusybox) || rootBusybox.length() != busyboxFile.length()) {
            busyboxFile.copyTo(rootBusybox, overwrite = true)
            rootBusybox.setExecutable(true, false)
        }

        val installer = privateFile("$INSTALL_DIR/install_busybox.sh")
        if (!installer.isFile || installer.length() <= 0L) {
            copyAssetText("$ASSET_DIR/install_busybox.sh", "$INSTALL_DIR/install_busybox.sh")
            installer.setExecutable(true, false)
        }

        val dirQ = dir.absolutePath.shellQuote()
        val scriptOut = ReusableShells.execSync(
            "chmod 755 ${busyboxFile.absolutePath.shellQuote()} 2>/dev/null; " +
                "sh ${installer.absolutePath.shellQuote()} $dirQ; echo __BB_SCRIPT:\$?",
        )
        logD("install_busybox.sh => $scriptOut", TAG)

        val scriptOk = scriptOut.contains("busybox_install already done") ||
            scriptOut.contains("busybox_install ok") ||
            scriptOut.contains("__BB_SCRIPT:0")

        if (!scriptOk && !looksLikeBusyboxTree(dir)) {
            val inlineOut = ReusableShells.execSync(
                "cd $dirQ && chmod 755 ./busybox && " +
                    "./busybox --list | while read applet; do " +
                    "case \"\$applet\" in sh|busybox|shell|swapon|swapoff|mkswap) ;; " +
                    "*) ./busybox ln -sf busybox \"\$applet\" 2>/dev/null; chmod 755 \"\$applet\" 2>/dev/null ;; esac; " +
                    "done && " +
                    "./busybox ln -sf busybox $LEGACY_SYMLINK_MARK; echo __BB_INLINE:\$?",
            )
            logD("busybox inline install => $inlineOut", TAG)
        }

        val ready = isUsableBinary(busyboxFile) && (scriptOk || looksLikeBusyboxTree(dir))
        if (ready) {
            writeReadyMark(dir)
            injectToolkitPath(dir)
            logD("private busybox installed", TAG)
        } else {
            logE("private busybox install failed", null, TAG)
        }
        return ready
    }

    /**
     * 仅本地判断，不跑 shell。
     * READY_MARK 为应用进程写入的普通文件；若仅有旧版 symlink 树，用 applet 数量启发式并补写标记。
     */
    private fun isPrivateBusyboxReady(dir: File, busyboxFile: File): Boolean {
        if (!isUsableBinary(busyboxFile)) return false
        val mark = File(dir, READY_MARK)
        if (mark.isFile && mark.length() >= 0L) {
            return true
        }
        if (looksLikeBusyboxTree(dir)) {
            writeReadyMark(dir)
            return true
        }
        return false
    }

    private fun looksLikeBusyboxTree(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val names = dir.list() ?: return false
        return names.size >= MIN_APPLET_ENTRIES && names.contains("busybox")
    }

    private fun writeReadyMark(dir: File) {
        runCatching {
            File(dir, READY_MARK).writeText("1\n")
        }
    }

    private fun isUsableBinary(file: File): Boolean =
        file.isFile && file.length() > 0L

    private fun injectToolkitPath(dir: File) {
        if (toolkitPathInjected) return
        ShellExecutor.setExtraEnvPath(dir.absolutePath)
        ReusableShells.tryExit()
        toolkitPathInjected = true
        logD("PATH+=${dir.absolutePath}", TAG)
    }

    private fun toolkitDir(): File = File(application.filesDir, INSTALL_DIR)

    private fun privateFile(relative: String): File =
        File(application.filesDir, relative.trimStart('/'))

    private fun copyAsset(assetPath: String, outRelative: String) {
        val dest = privateFile(outRelative)
        dest.parentFile?.mkdirs()
        application.assets.open(assetPath).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
        dest.setReadable(true, false)
        dest.setWritable(true)
        dest.setExecutable(true, false)
    }

    private fun copyAssetText(assetPath: String, outRelative: String) {
        val dest = privateFile(outRelative)
        dest.parentFile?.mkdirs()
        val text = application.assets.open(assetPath).bufferedReader().use { it.readText() }
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        dest.writeText(text)
        dest.setExecutable(true, false)
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
}
