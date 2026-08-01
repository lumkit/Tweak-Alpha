package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.ProcessInfo
import io.github.lumkit.tweak.model.withAppMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程管理（完整版）：列表含 RES/SWAP，支持详情（cpuset / cgroup / oom）与杀进程。
 *
 * 解析逻辑对齐 vtools [com.omarea.library.shell.ProcessUtils]；
 * 黑名单沿用本项目 [staticExcludes] + [isSelfAppProcess]。
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
        "toybox",
        "ps",
        "top",
        "tweak_server",
        "ub.lumkit.tweak",
        "[ub.lumkit.tweak]",
    )

    suspend fun supported(): Boolean = mutex.withLock {
        ensureProbed()
        !listCommand.isNullOrBlank() && !detailCommand.isNullOrBlank()
    }

    suspend fun getAllProcess(): List<ProcessInfo> = mutex.withLock {
        ensureProbed()
        val cmd = listCommand?.takeIf { it.isNotBlank() } ?: return emptyList()
        val rows = ReusableShells.execSync(cmd).split("\n")
        val processInfoList = ArrayList<ProcessInfo>()
        var isFirstRow = true
        for (row in rows) {
            if (isFirstRow) {
                isFirstRow = false
                continue
            }
            val processInfo = readRow(row.trim())
            if (processInfo != null) {
                processInfoList.add(processInfo)
            }
        }
        return processInfoList.withAppMeta()
    }

    suspend fun getProcessDetail(pid: Int): ProcessInfo? = mutex.withLock {
        ensureProbed()
        val prefix = detailCommand?.takeIf { it.isNotBlank() } ?: return null
        val response = ReusableShells.execSync("$prefix$pid")
        val rows = response.split("\n")
        if (rows.size <= 1) {
            return null
        }
        val row = readRow(rows[1].trim()) ?: return null
        val status = KernelProps.getProp("/proc/$pid/status")
        return row.copy(
            state = parseProcStatusField(status, "State"),
            cpuSet = KernelProps.getProp("/proc/$pid/cpuset"),
            cGroup = KernelProps.getProp("/proc/$pid/cgroup"),
            oomAdj = KernelProps.getProp("/proc/$pid/oom_adj"),
            oomScore = KernelProps.getProp("/proc/$pid/oom_score"),
            oomScoreAdj = KernelProps.getProp("/proc/$pid/oom_score_adj"),
            cpus = parseProcStatusField(status, "Cpus_allowed_list"),
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

    private fun parseProcStatusField(status: String, key: String): String {
        val prefix = "$key:"
        return status.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.trim()
            .orEmpty()
    }

    private suspend fun ensureProbed() {
        if (probed) {
            return
        }
        probed = true
        listCommand = ""
        detailCommand = ""

        val outsideToybox = ToolkitInstaller.ensureToybox()
        val perfectCmd = "top -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE -q -b -n 1 -m 65535"
        val outsidePerfectCmd =
            if (outsideToybox.isNotBlank()) "$outsideToybox $perfectCmd" else ""
        val insideCmd = "ps -e -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE"
        val outsideCmd =
            if (outsideToybox.isNotBlank()) "$outsideToybox $insideCmd" else ""

        for (cmd in listOf(outsidePerfectCmd, perfectCmd, outsideCmd, insideCmd)) {
            if (cmd.isBlank()) continue
            if (isUsableListCommand(cmd)) {
                listCommand = cmd
                break
            }
        }

        for (cmd in listOf(outsideCmd, insideCmd)) {
            if (cmd.isBlank()) continue
            if (isUsableListCommand(cmd)) {
                detailCommand = "$cmd --pid "
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

    /** 对齐 vtools：`%CPU RES SWAP NAME PID USER COMMAND CMDLINE…` */
    private fun readRow(row: String): ProcessInfo? {
        val columns = row.split(whitespaceRegex)
        if (columns.size < 6) {
            return null
        }
        return try {
            val name = columns[3]
            val command = columns.getOrElse(6) { "" }
            val cmdline = if (command.isNotEmpty()) {
                val idx = row.indexOf(command)
                if (idx >= 0) {
                    row.substring(idx + command.length).trim()
                } else {
                    ""
                }
            } else {
                ""
            }
            if (isExcluded(name, command, cmdline)) {
                return null
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
        } catch (_: Exception) {
            null
        }
    }

    private fun isExcluded(name: String, command: String, cmdline: String): Boolean {
        return name in staticExcludes ||
            command.substringAfterLast('/') in staticExcludes ||
            isSelfAppProcess(name, command, cmdline)
    }

    private fun str2Long(str: String): Long {
        return when {
            str.contains("K") -> str.substringBefore("K").toDouble().toLong()
            str.contains("M") -> (str.substringBefore("M").toDouble() * 1024).toLong()
            str.contains("G") -> (str.substringBefore("G").toDouble() * 1_048_576).toLong()
            else -> str.toLong() / 1024
        }
    }

    private fun String.shellArg(): String = "'${replace("'", "'\\''")}'"
}
