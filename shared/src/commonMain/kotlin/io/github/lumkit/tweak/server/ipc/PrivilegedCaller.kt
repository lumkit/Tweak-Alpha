package io.github.lumkit.tweak.server.ipc

private const val ROOT_UID = 0
private const val SHELL_UID = 2000

fun isPrivilegedCaller(callingUid: Int): Boolean {
    return callingUid == ROOT_UID || callingUid == SHELL_UID
}
