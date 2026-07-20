package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.ProcessInfo
import io.github.lumkit.tweak.model.withAppMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程管理（完整版）：列表含 RES/SWAP，支持详情（cpuset / cgroup / oom）与杀进程。
 */
object ProcessUtils {
    private val mutex = Mutex()
    private val whitespaceRegex = Regex("\\s+")

    private var listCommand: String? = null
    private var detailCommand: String? = null
    private var probed = false

    private val staticExcludes = setOf(
        "toybox-outside",
        "toybox-outside64",
        "ps",
        "top",
    )

    suspend fun supported(): Boolean = mutex.withLock {
        ensureProbed()
        !listCommand.isNullOrBlank() && !detailCommand.isNullOrBlank()
    }

    suspend fun getAllProcess(): List<ProcessInfo> = mutex.withLock {
        ensureProbed()
        val cmd = listCommand?.takeIf { it.isNotBlank() } ?: return emptyList()
        val rows = ReusableShells.execSync(cmd).lineSequence()
        rows
            .drop(1)
            .mapNotNull { readRow(it.trim()) }
            .toList()
            .withAppMeta()
    }

    suspend fun getProcessDetail(pid: Int): ProcessInfo? = mutex.withLock {
        ensureProbed()
        val prefix = detailCommand?.takeIf { it.isNotBlank() } ?: return null
        val rows = ReusableShells.execSync("$prefix$pid")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (rows.size <= 1) {
            return null
        }
        val row = readRow(rows[1]) ?: return null
        return row.copy(
            cpuSet = KernelProps.getProp("/proc/$pid/cpuset"),
            cGroup = KernelProps.getProp("/proc/$pid/cgroup"),
            oomAdj = KernelProps.getProp("/proc/$pid/oom_adj"),
            oomScore = KernelProps.getProp("/proc/$pid/oom_score"),
            oomScoreAdj = KernelProps.getProp("/proc/$pid/oom_score_adj"),
        ).withAppMeta()
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
        listCommand = null
        detailCommand = null
        probed = false
    }

    private suspend fun ensureProbed() {
        if (probed) {
            return
        }
        probed = true
        listCommand = ""
        detailCommand = ""

        val perfectCmd = "top -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE -q -b -n 1 -m 65535"
        val insideCmd = "ps -e -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE"

        for (cmd in listOf(perfectCmd, insideCmd)) {
            if (isUsableListCommand(cmd)) {
                listCommand = cmd
                break
            }
        }

        for (cmd in listOf(insideCmd)) {
            if (isUsableListCommand(cmd)) {
                detailCommand = "$cmd --pid "
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
        if (columns.size < 6) {
            return null
        }
        return runCatching {
            val name = columns[3]
            if (isExcluded(name)) {
                return null
            }
            val command = columns.getOrElse(6) { "" }
            val cmdline = if (command.isNotEmpty() && row.contains(command)) {
                row.substring(row.indexOf(command) + command.length).trim()
            } else {
                ""
            }
            ProcessInfo(
                cpu = columns[0].toFloat(),
                res = str2Long(columns[1]),
                swap = str2Long(columns[2]),
                name = name,
                pid = columns[4].toInt(),
                user = columns[5],
                command = command,
                cmdline = cmdline,
            )
        }.getOrNull()
    }

    private fun isExcluded(name: String): Boolean {
        return name in staticExcludes || name == packageName
    }

    private fun str2Long(str: String): Long {
        return when {
            str.contains('K') -> str.substringBefore('K').toDouble().toLong()
            str.contains('M') -> (str.substringBefore('M').toDouble() * 1024).toLong()
            str.contains('G') -> (str.substringBefore('G').toDouble() * 1_048_576).toLong()
            else -> str.toLong() / 1024
        }
    }

    private fun String.shellArg(): String = "'${replace("'", "'\\''")}'"
}
