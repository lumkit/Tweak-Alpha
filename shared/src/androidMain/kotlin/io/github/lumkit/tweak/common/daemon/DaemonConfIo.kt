package io.github.lumkit.tweak.common.daemon

import android.util.Base64
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.PrivilegedWorkDir
import io.github.lumkit.tweak.common.utils.logD
import java.io.File

/**
 * 写入 daemon conf：优先走特权 shell（Shizuku/Root），避免 NativeFileService
 * 在 shell_data_file / 属主不一致时出现 Permission denied。
 */
internal object DaemonConfIo {

    private const val TAG = "DaemonConfIo"

    suspend fun write(
        path: String,
        text: String,
        workRoot: String,
        dir: String,
        dirMode: String,
        isRootPrivate: Boolean,
    ) {
        if (isRootPrivate) {
            File(workRoot).mkdirs()
            File(dir).mkdirs()
            File(path).writeText(text)
            return
        }

        PrivilegedWorkDir.ensureWritable(
            path = dir,
            workRoot = workRoot,
            mode = dirMode,
        )

        val q = path.shellQuote()
        val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val shellOut = runCatching {
            ReusableShells.execSync(
                "rm -f $q; " +
                    "printf '%s' '$b64' | base64 -d > $q && " +
                    "chmod 666 $q && " +
                    "cat $q",
            )
        }.getOrNull()

        val written = shellOut?.replace("\r\n", "\n")?.trimEnd('\n')
        if (written == text.trimEnd('\n')) {
            logD("wrote via shell path=$path", TAG)
            return
        }

        logD(
            "shell write mismatch, fallback Files path=$path shellOut=${shellOut?.take(80)}",
            TAG,
        )
        Files.writeText(path, text).throwIfFailed("writeText $path")
        Files.chmod(path, "0666")
        // 再强制 shell chmod，避免 Files.chmod 无效
        runCatching {
            ReusableShells.execSync("chmod 666 $q 2>/dev/null || true")
        }
    }

    private fun NativeFileResult<*>.throwIfFailed(op: String) {
        if (this is NativeFileResult.Failure) {
            error("$op failed: ${error.message}")
        }
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
}
