package io.github.lumkit.tweak.common.utils

/**
 * 是否为本应用进程（主进程或 `package:子进程`）。
 * 用于进程列表过滤，避免把自己和多进程组件暴露出来。
 */
internal fun isSelfAppProcess(
    name: String,
    command: String = "",
    cmdline: String = "",
): Boolean {
    if (matchesSelfProcessToken(name)) {
        return true
    }
    if (matchesSelfProcessToken(command.substringAfterLast('/'))) {
        return true
    }
    val cmd0 = cmdline
        .substringBefore('\u0000')
        .substringBefore(' ')
        .substringAfterLast('/')
    return matchesSelfProcessToken(cmd0)
}

private fun matchesSelfProcessToken(token: String): Boolean {
    if (token.isBlank()) {
        return false
    }
    val pkg = packageName
    return token == pkg || token.startsWith("$pkg:")
}
