package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.ProcessInfo
import io.github.lumkit.tweak.model.withAppMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程管理（完整版）：列表含 RES/SWAP，支持详情（cpuset / cgroup / oom）与杀进程。
 *
 * 列表列刻意 **不包含 NAME**：toybox/top 对 NAME 固定列宽，CJK（如 `exec -a 测试`）
 * 易与 PID 粘连或错位，导致整行解析失败。显示名改从 COMMAND / CMDLINE 推导。
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
    )

    suspend fun supported(): Boolean = mutex.withLock {
        ensureProbed()
        !listCommand.isNullOrBlank() && !detailCommand.isNullOrBlank()
    }

    suspend fun getAllProcess(): List<ProcessInfo> = mutex.withLock {
        ensureProbed()
        val cmd = listCommand?.takeIf { it.isNotBlank() } ?: return emptyList()
        val rows = ReusableShells.execSync(cmd).lineSequence().toList()
        if (rows.isEmpty()) return emptyList()
        val start = if (isHeaderRow(rows.first())) 1 else 0
        rows
            .drop(start)
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
        if (rows.isEmpty()) {
            return null
        }
        val dataRow = rows.firstOrNull { !isHeaderRow(it) } ?: return null
        val row = readRow(dataRow) ?: return null
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
        // 无 NAME：避免 CJK 进程名弄乱列对齐
        val perfectCmd = "top -o %CPU,RES,SWAP,PID,USER,COMMAND,CMDLINE -q -b -n 1 -m 65535"
        val insideCmd = "ps -e -o %CPU,RES,SWAP,PID,USER,COMMAND,CMDLINE"
        // 兼容旧 toybox：仍带 NAME 的格式作回退
        val legacyPerfectCmd = "top -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE -q -b -n 1 -m 65535"
        val legacyInsideCmd = "ps -e -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE"

        val listCandidates = buildList {
            if (outsideToybox.isNotBlank()) {
                add(outsideToybox to perfectCmd)
                add(outsideToybox to insideCmd)
            }
            add("" to perfectCmd)
            add("" to insideCmd)
            if (outsideToybox.isNotBlank()) {
                add(outsideToybox to legacyPerfectCmd)
                add(outsideToybox to legacyInsideCmd)
            }
            add("" to legacyPerfectCmd)
            add("" to legacyInsideCmd)
        }
        for ((prefix, args) in listCandidates) {
            val cmd = if (prefix.isBlank()) args else "$prefix $args"
            if (isUsableListCommand(cmd)) {
                listCommand = cmd
                break
            }
        }

        val detailCandidates = buildList {
            if (outsideToybox.isNotBlank()) {
                add("$outsideToybox $insideCmd")
                add("$outsideToybox $legacyInsideCmd")
            }
            add(insideCmd)
            add(legacyInsideCmd)
        }
        for (cmd in detailCandidates) {
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

    private fun isHeaderRow(row: String): Boolean {
        val t = row.trim()
        return t.contains("CPU", ignoreCase = true) &&
            (t.contains("PID", ignoreCase = true) || t.contains("NAME", ignoreCase = true))
    }

    private fun readRow(row: String): ProcessInfo? {
        if (row.isBlank() || isHeaderRow(row)) {
            return null
        }
        val columns = row.split(whitespaceRegex).filter { it.isNotEmpty() }
        if (columns.size < 5) {
            return null
        }
        return runCatching {
            val parsed = parseRowColumns(columns) ?: return null
            if (isExcluded(parsed.name, parsed.command, parsed.cmdline)) {
                return null
            }
            ProcessInfo(
                cpu = columns[0].toFloat(),
                res = str2Long(columns[1]),
                swap = str2Long(columns[2]),
                name = parsed.name,
                pid = parsed.pid,
                user = parsed.user,
                command = parsed.command,
                cmdline = parsed.cmdline,
            )
        }.getOrNull()
    }

    /**
     * 支持两种列布局：
     * 1) 无 NAME：CPU RES SWAP PID USER COMMAND CMDLINE…
     * 2) 有 NAME：CPU RES SWAP NAME PID USER COMMAND CMDLINE…（含 NAME+PID 粘连兜底）
     */
    private fun parseRowColumns(columns: List<String>): ParsedRow? {
        // 优先：无 NAME（index3 即为 PID）
        columns.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 }?.let { pid ->
            val user = columns.getOrElse(4) { "" }
            // USER 不应是纯数字（否则更像「NAME=数字, PID=下一项」的旧布局）
            if (user.toIntOrNull() == null) {
                val command = columns.getOrElse(5) { "" }
                val cmdline = columns.drop(6).joinToString(" ")
                return ParsedRow(
                    name = deriveName(command, cmdline),
                    pid = pid,
                    user = user,
                    command = command,
                    cmdline = cmdline,
                )
            }
        }

        // 旧布局：带 NAME
        return parseLegacyNameLayout(columns)
    }

    private fun parseLegacyNameLayout(columns: List<String>): ParsedRow? {
        // 从 index≥3 扫描：PID 为数字且后一项不是纯数字（USER）
        // exec -a 123 时 NAME 也是数字，此时连续两个数字，后者为 PID
        var i = 3
        while (i < columns.size - 1) {
            val asPid = columns[i].toIntOrNull()
            if (asPid != null && asPid > 0) {
                val next = columns[i + 1]
                val nextDigits = next.toIntOrNull() != null
                if (nextDigits && i + 2 < columns.size && columns[i + 2].toIntOrNull() == null) {
                    val name = columns.subList(3, i + 1).joinToString(" ")
                    val pid = columns[i + 1].toInt()
                    val user = columns[i + 2]
                    val command = columns.getOrElse(i + 3) { "" }
                    val cmdline = columns.drop(i + 4).joinToString(" ")
                    return ParsedRow(name, pid, user, command, cmdline)
                }
                if (!nextDigits) {
                    val name = if (i > 3) {
                        columns.subList(3, i).joinToString(" ")
                    } else {
                        ""
                    }
                    val command = columns.getOrElse(i + 2) { "" }
                    val cmdline = columns.drop(i + 3).joinToString(" ")
                    val display = name.ifBlank { deriveName(command, cmdline) }
                    return ParsedRow(display, asPid, next, command, cmdline)
                }
            } else {
                // NAME+PID 粘连：测试12345
                val glued = gluedNamePidRegex.matchEntire(columns[i])
                if (glued != null && i + 1 < columns.size && columns[i + 1].toIntOrNull() == null) {
                    val pid = glued.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
                    val command = columns.getOrElse(i + 2) { "" }
                    val cmdline = columns.drop(i + 3).joinToString(" ")
                    return ParsedRow(
                        name = glued.groupValues[1],
                        pid = pid,
                        user = columns[i + 1],
                        command = command,
                        cmdline = cmdline,
                    )
                }
            }
            i++
        }
        return null
    }

    private val gluedNamePidRegex = Regex("""^(.*\D)(\d{2,7})$""")

    private fun deriveName(command: String, cmdline: String): String {
        val fromCmdline = cmdline
            .substringBefore('\u0000')
            .trim()
            .substringBefore(' ')
            .substringAfterLast('/')
            .trim()
        if (fromCmdline.isNotBlank()) {
            return fromCmdline
        }
        val fromCommand = command.substringAfterLast('/').trim()
        return fromCommand
    }

    private data class ParsedRow(
        val name: String,
        val pid: Int,
        val user: String,
        val command: String,
        val cmdline: String,
    )

    private fun isExcluded(name: String, command: String, cmdline: String): Boolean {
        return name in staticExcludes ||
            command.substringAfterLast('/') in staticExcludes ||
            isSelfAppProcess(name, command, cmdline)
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
