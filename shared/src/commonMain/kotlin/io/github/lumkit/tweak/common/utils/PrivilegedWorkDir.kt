package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells

/**
 * 特权工作区目录准备。
 *
 * - Shizuku：`0777`/`0707`，便于 shell 复用 tmp 目录
 * - Root 私有目录：`0700`，避免其它 UID 写入
 */
object PrivilegedWorkDir {

    private const val TAG = "PrivilegedWorkDir"

    /**
     * @param workRoot 工作区根（如应用 filesDir/tweak-alpha 或 tmp 根）
     * @param path 要确保可写的目录（通常为 daemon 子目录）
     * @param mode 目标权限，如 `0700` / `0777`
     */
    suspend fun ensureWritable(
        path: String,
        workRoot: String,
        mode: String = "0777",
    ) {
        val normalizedMode = mode.trim().trimStart('0').padStart(3, '0').takeLast(3)

        if (Files.exists(path).getOrNull() == true) {
            val current = readModeOctal(path)
            if (!isAcceptableMode(current, normalizedMode)) {
                logD("recreate dir mode=${current ?: "unknown"} want=$normalizedMode path=$path", TAG)
                recreateDirectory(path)
            }
        }

        shellMkdirAndChmod(workRoot, path, normalizedMode)

        when (val mkdir = Files.mkdirs(path)) {
            is NativeFileResult.Success -> Unit
            is NativeFileResult.Failure -> {
                shellMkdirAndChmod(workRoot, path, normalizedMode)
                when (val retry = Files.mkdirs(path)) {
                    is NativeFileResult.Success -> Unit
                    is NativeFileResult.Failure -> {
                        error(buildPermissionHint(path, workRoot, retry.error.message))
                    }
                }
            }
        }

        Files.chmod(workRoot, normalizedMode)
        Files.chmod(path, normalizedMode)
        shellMkdirAndChmod(workRoot, path, normalizedMode)

        val modeAfter = readModeOctal(path)
        if (!isAcceptableMode(modeAfter, normalizedMode)) {
            logD("chmod ineffective mode=${modeAfter ?: "unknown"}, force recreate $path", TAG)
            recreateDirectory(path)
            shellMkdirAndChmod(workRoot, path, normalizedMode)
            Files.mkdirs(path).let { result ->
                if (result is NativeFileResult.Failure) {
                    error(buildPermissionHint(path, workRoot, result.error.message))
                }
            }
            Files.chmod(path, normalizedMode)
            shellMkdirAndChmod(workRoot, path, normalizedMode)
        }

        if (!probeWritable(path)) {
            error(buildPermissionHint(path, workRoot, "directory not writable by current privilege"))
        }
    }

    private suspend fun recreateDirectory(path: String) {
        when (val deleted = Files.delete(path, recursive = true)) {
            is NativeFileResult.Success -> Unit
            is NativeFileResult.Failure -> {
                logD("Files.delete failed: ${deleted.error.message}, fallback rm -rf", TAG)
            }
        }
        runCatching {
            ReusableShells.execSync("rm -rf ${path.shellQuote()} 2>/dev/null || true")
        }.onFailure {
            logD("shell rm skipped: ${it.message}", TAG)
        }
        if (Files.exists(path).getOrNull() == true) {
            error("failed to remove directory with unacceptable mode: $path")
        }
    }

    private suspend fun readModeOctal(path: String): String? {
        val fromStat = runCatching {
            ReusableShells.execSync(
                "stat -c '%a' ${path.shellQuote()} 2>/dev/null || " +
                    "busybox stat -c '%a' ${path.shellQuote()} 2>/dev/null || true",
            ).trim().lines().lastOrNull()?.trim().orEmpty()
        }.getOrDefault("")

        val digits = fromStat.takeLast(3)
        if (digits.length == 3 && digits.all { it in '0'..'7' }) {
            return digits
        }

        val ls = runCatching {
            ReusableShells.execSync("ls -ld ${path.shellQuote()} 2>/dev/null || true")
                .trim()
                .lines()
                .lastOrNull()
                .orEmpty()
        }.getOrDefault("")
        return parseLsMode(ls)
    }

    private fun parseLsMode(lsLine: String): String? {
        if (lsLine.length < 10) return null
        val perms = lsLine.substring(1, 10)
        if (perms.length != 9) return null
        fun triplet(offset: Int): Int {
            var v = 0
            if (perms[offset] == 'r') v = v or 4
            if (perms[offset + 1] == 'w') v = v or 2
            if (perms[offset + 2] == 'x' || perms[offset + 2] == 's' || perms[offset + 2] == 't') {
                v = v or 1
            }
            return v
        }
        return "${triplet(0)}${triplet(3)}${triplet(6)}"
    }

    private fun isAcceptableMode(actual: String?, wanted: String): Boolean {
        if (actual == null) return false
        if (actual == wanted) return true
        // Shizuku 共享目录：777/707 均可
        if (wanted == "777" && (actual == "777" || actual == "707")) return true
        // Root 私有：700 即可；755 若仅 owner 可写也勉强可接受，仍重建为 700
        return false
    }

    private suspend fun shellMkdirAndChmod(root: String, path: String, mode: String) {
        runCatching {
            ReusableShells.execSync(
                "mkdir -p ${path.shellQuote()} && " +
                    "chmod $mode ${root.shellQuote()} ${path.shellQuote()} 2>/dev/null || true",
            )
        }.onFailure {
            logD("shell mkdir/chmod skipped: ${it.message}", TAG)
        }
    }

    private suspend fun probeWritable(dir: String): Boolean {
        val probe = dir.trimEnd('/') + "/.tweak_write_probe"
        return when (val write = Files.writeText(probe, "ok")) {
            is NativeFileResult.Success -> {
                Files.delete(probe)
                true
            }
            is NativeFileResult.Failure -> {
                logE("write probe failed: ${write.error.message}", null, TAG)
                false
            }
        }
    }

    private fun buildPermissionHint(path: String, workRoot: String, detail: String?): String {
        return "无法写入工作目录 $path（$detail）。" +
            "请确认当前特权模式与目录匹配（Root→应用数据目录，Shizuku→tmp），或执行：" +
            "rm -rf $workRoot  后重试。"
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"
}
