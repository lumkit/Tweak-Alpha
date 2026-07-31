package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.ProcessInfo
import io.github.lumkit.tweak.model.ThreadInfo
import io.github.lumkit.tweak.model.withAppMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程管理（轻量版）：短字段列表、应用主进程 PID、线程 CPU Top15。
 */
object ProcessUtilLite {
    private val mutex = Mutex()
    private val whitespaceRegex = Regex("\\s+")

    private var psCommand: String? = null
    private var probed = false

    private val staticExcludes = setOf(
        "toybox-outside",
        "toybox-outside64",
        "toybox",
        "ps",
        "top",
        "tweak_server",
        "ub.lumkit.tweak",
        "[ub.lumkit.tweak]",
    )

    suspend fun supported(): Boolean = mutex.withLock {
        ensureProbed()
        !psCommand.isNullOrBlank()
    }

    suspend fun getAllProcess(): List<ProcessInfo> = mutex.withLock {
        ensureProbed()
        val cmd = psCommand?.takeIf { it.isNotBlank() } ?: return emptyList()
        val rows = ReusableShells.execSync(cmd).lineSequence().toList()
        if (rows.isEmpty()) {
            return emptyList()
        }

        val startIndex = if (rows.first().trim().let {
                it.contains("CPU", ignoreCase = true) &&
                    (it.contains("PID", ignoreCase = true) || it.contains("NAME", ignoreCase = true))
            }
        ) {
            1
        } else {
            0
        }

        rows
            .drop(startIndex)
            .mapNotNull { readRow(it.trim()) }
            .toList()
            .withAppMeta()
    }

    suspend fun getAppMainProcess(appPackage: String): Int {
        if (appPackage.isBlank()) {
            return -1
        }
        val pid = ReusableShells.execSync(
            "ps -ef -o PID,NAME | grep -e ${"$appPackage\$".shellArg()} | egrep -o '[0-9]{1,}' | head -n 1",
        ).trim()
        if (pid.isEmpty() || pid == "error") {
            return -1
        }
        return pid.toIntOrNull() ?: -1
    }

    suspend fun getThreadLoads(pid: Int): List<ThreadInfo> {
        val result = ReusableShells.execSync(
            "top -H -b -q -n 1 -p $pid -o TID,%CPU,CMD",
        )
        val threadData = result
            .lineSequence()
            .mapNotNull { row ->
                val rowStr = row.trim()
                val cols = rowStr.split(whitespaceRegex)
                if (cols.size <= 2) {
                    return@mapNotNull null
                }
                runCatching {
                    val cpuToken = cols[1]
                    val name = rowStr
                        .substring(rowStr.indexOf(cpuToken) + cpuToken.length)
                        .trim()
                    ThreadInfo(
                        tid = cols[0].toInt(),
                        cpuLoad = cpuToken.toDouble(),
                        name = name,
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.cpuLoad }
            .toList()
        return threadData.take(15)
    }

    suspend fun killProcess(pid: Int) {
        ReusableShells.execSync("kill -9 $pid")
    }

    suspend fun killProcess(processInfo: ProcessInfo) {
        if (processInfo.isAndroidProcess) {
            val pkg = processInfo.appPackageName
            ReusableShells.execSync(
                "killall -9 ${pkg.shellArg()};am force-stop ${pkg.shellArg()};am kill ${pkg.shellArg()}",
            )
        } else {
            killProcess(processInfo.pid)
        }
    }

    suspend fun reset() = mutex.withLock {
        psCommand = null
        probed = false
    }

    private suspend fun ensureProbed() {
        if (probed) {
            return
        }
        probed = true
        psCommand = ""

        val outsideToybox = ToolkitInstaller.ensureToybox()
        // 无 NAME，避免 CJK 改名弄乱列
        val perfectCmd = "top -o %CPU,PID,USER,COMMAND -q -b -n 1 -m 65535"
        val insideCmd = "ps -e -o %CPU,PID,USER,COMMAND"
        val perfectCmdNoUser = "top -o %CPU,PID,COMMAND -q -b -n 1 -m 65535"
        val insideCmdNoUser = "ps -e -o %CPU,PID,COMMAND"
        val legacyPerfectCmd = "top -o %CPU,NAME,COMMAND,PID,USER -q -b -n 1 -m 65535"
        val legacyInsideCmd = "ps -e -o %CPU,NAME,COMMAND,PID,USER"

        val candidates = buildList {
            if (outsideToybox.isNotBlank()) {
                add("$outsideToybox $perfectCmd")
                add("$outsideToybox $perfectCmdNoUser")
            }
            add(perfectCmd)
            add(insideCmd)
            add(perfectCmdNoUser)
            add(insideCmdNoUser)
            if (outsideToybox.isNotBlank()) {
                add("$outsideToybox $legacyPerfectCmd")
                add("$outsideToybox $legacyInsideCmd")
            }
            add(legacyPerfectCmd)
            add(legacyInsideCmd)
        }
        for (cmd in candidates) {
            if (isUsableListCommand(cmd)) {
                psCommand = cmd
                break
            }
        }
    }

    private suspend fun isUsableListCommand(cmd: String): Boolean {
        val rows = ReusableShells.execSync("$cmd 2>&1").lineSequence().toList()
        if (rows.size <= 10) {
            return false
        }
        val head = rows.firstOrNull().orEmpty()
        return !(head.contains("bad -o") ||
            head.contains("Unknown option") ||
            head.contains("bad"))
    }

    private fun readRow(row: String): ProcessInfo? {
        val columns = row.split(whitespaceRegex).filter { it.isNotEmpty() }
        if (columns.size < 3) {
            return null
        }
        return runCatching {
            // 新布局：CPU PID USER COMMAND… 或 CPU PID COMMAND…
            val pidAt1 = columns.getOrNull(1)?.toIntOrNull()
            if (pidAt1 != null && pidAt1 > 0) {
                val maybeUser = columns.getOrElse(2) { "" }
                val command: String
                val user: String
                if (maybeUser.toIntOrNull() == null && columns.size >= 4) {
                    user = maybeUser
                    command = columns.drop(3).joinToString(" ")
                } else {
                    user = ""
                    command = columns.drop(2).joinToString(" ")
                }
                val name = command.substringAfterLast('/').ifBlank { command }
                if (isExcluded(name, command)) return null
                return@runCatching ProcessInfo(
                    cpu = columns[0].toFloat(),
                    name = name,
                    command = command,
                    pid = pidAt1,
                    user = user,
                )
            }

            // 旧布局：CPU NAME COMMAND PID [USER]
            val pidDirect = columns.getOrNull(3)?.toIntOrNull()
            val name: String
            val command: String
            val pid: Int
            val user: String
            if (pidDirect != null && pidDirect > 0) {
                name = columns[1]
                command = columns[2]
                pid = pidDirect
                user = columns.getOrElse(4) { "" }
            } else {
                val pidAlt = columns.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
                name = columns[1]
                command = ""
                pid = pidAlt
                user = columns.getOrElse(3) { "" }
            }
            if (isExcluded(name, command)) {
                return null
            }
            ProcessInfo(
                cpu = columns[0].toFloat(),
                name = name,
                command = command,
                pid = pid,
                user = user,
            )
        }.getOrNull()
    }

    private fun isExcluded(name: String, command: String): Boolean {
        return name in staticExcludes || isSelfAppProcess(name, command)
    }

    private fun String.shellArg(): String = "'${replace("'", "'\\''")}'"
}
