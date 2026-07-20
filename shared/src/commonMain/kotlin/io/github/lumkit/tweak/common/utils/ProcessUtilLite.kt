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
        "ps",
        "top",
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

        val startIndex = if (rows.first().trim().let { it.contains("CPU") && it.contains("NAME") }) {
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

        val perfectCmd = "top -o %CPU,NAME,COMMAND,PID,USER -q -b -n 1 -m 65535"
        val insideCmd = "ps -e -o %CPU,NAME,COMMAND,PID,USER"
        // 旧设备可能不支持 USER 列
        val perfectCmdNoUser = "top -o %CPU,NAME,COMMAND,PID -q -b -n 1 -m 65535"
        val insideCmdNoUser = "ps -e -o %CPU,NAME,COMMAND,PID"

        for (cmd in listOf(perfectCmd, insideCmd, perfectCmdNoUser, insideCmdNoUser)) {
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
        val columns = row.split(whitespaceRegex)
        if (columns.size < 4) {
            return null
        }
        return runCatching {
            val name = columns[1]
            if (isExcluded(name)) {
                return null
            }
            ProcessInfo(
                cpu = columns[0].toFloat(),
                name = name,
                command = columns[2],
                pid = columns[3].toInt(),
                user = columns.getOrElse(4) { "" },
            )
        }.getOrNull()
    }

    private fun isExcluded(name: String): Boolean {
        return name in staticExcludes || name == packageName
    }

    private fun String.shellArg(): String = "'${replace("'", "'\\''")}'"
}
