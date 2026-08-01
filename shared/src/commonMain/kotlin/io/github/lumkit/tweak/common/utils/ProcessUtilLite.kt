package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.ProcessInfo
import io.github.lumkit.tweak.model.ThreadInfo
import io.github.lumkit.tweak.model.withAppMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程管理（轻量版）：短字段列表、应用主进程 PID、线程 CPU Top15。
 *
 * 解析逻辑对齐 vtools [com.omarea.library.shell.ProcessUtils2]；
 * 黑名单沿用本项目 [staticExcludes] + [isSelfAppProcess]。
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
        val rows = ReusableShells.execSync(cmd).split("\n")
        val processInfoList = ArrayList<ProcessInfo>()
        var isFirstRow = true
        for (row in rows) {
            if (isFirstRow) {
                isFirstRow = false
                if (row.trim().contains("CPU") && row.trim().contains("NAME")) {
                    continue
                }
            }
            val processInfo = readRow(row.trim())
            if (processInfo != null) {
                processInfoList.add(processInfo)
            }
        }
        return processInfoList.withAppMeta()
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
        val threadData = ArrayList<ThreadInfo>()
        for (row in result.split("\n")) {
            val rowStr = row.trim()
            val cols = rowStr.split(whitespaceRegex)
            if (cols.size <= 2) {
                continue
            }
            try {
                val cpuToken = cols[1]
                val name = rowStr
                    .substring(rowStr.indexOf(cpuToken) + cpuToken.length)
                    .trim()
                threadData.add(
                    ThreadInfo(
                        tid = cols[0].toInt(),
                        cpuLoad = cpuToken.toDouble(),
                        name = name,
                    ),
                )
            } catch (_: Exception) {
                // ignore malformed rows
            }
        }
        threadData.sortByDescending { it.cpuLoad }
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
        val perfectCmd = "top -o %CPU,NAME,COMMAND,PID -q -b -n 1 -m 65535"
        val outsidePerfectCmd =
            if (outsideToybox.isNotBlank()) "$outsideToybox $perfectCmd" else ""
        val insideCmd = "ps -e -o %CPU,NAME,COMMAND,PID"
        val outsideCmd =
            if (outsideToybox.isNotBlank()) "$outsideToybox $insideCmd" else ""

        for (cmd in listOf(outsidePerfectCmd, perfectCmd, outsideCmd, insideCmd)) {
            if (cmd.isBlank()) continue
            if (isUsableListCommand(cmd)) {
                psCommand = cmd
                break
            }
        }
    }

    private suspend fun isUsableListCommand(cmd: String): Boolean {
        val rows = ReusableShells.execSync("$cmd 2>&1").split("\n")
        if (rows.size <= 10) {
            return false
        }
        val head = rows.firstOrNull().orEmpty()
        return !(head.contains("bad -o") ||
            head.contains("Unknown option") ||
            head.contains("bad"))
    }

    /** 对齐 vtools：`%CPU NAME COMMAND PID` */
    private fun readRow(row: String): ProcessInfo? {
        val columns = row.split(whitespaceRegex)
        if (columns.size < 3) {
            return null
        }
        return try {
            val name = columns[1]
            val command = columns[2]
            if (isExcluded(name, command)) {
                return null
            }
            ProcessInfo(
                cpu = columns[0].toFloat(),
                name = name,
                command = command,
                pid = columns[3].toInt(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isExcluded(name: String, command: String): Boolean {
        return name in staticExcludes ||
            command.substringAfterLast('/') in staticExcludes ||
            isSelfAppProcess(name, command)
    }

    private fun String.shellArg(): String = "'${replace("'", "'\\''")}'"
}
