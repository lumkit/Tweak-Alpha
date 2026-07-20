package io.github.lumkit.tweak.model

import androidx.compose.runtime.Immutable
import io.github.lumkit.tweak.common.utils.AppsHelper

/**
 * Android 应用沙盒用户名：`u{userId}_a{appId}`（如 u0_a123）。
 * 隔离进程：`u{userId}_i{isolatedId}`。
 */
private val androidAppUserRegex = Regex("""^u\d+_a\d+$""")
private val androidIsolatedUserRegex = Regex("""^u\d+_i\d+$""")
/** 宽松匹配：任意 uN_ 前缀（含 a/i 及其他变体） */
private val androidSandboxUserRegex = Regex("""^u\d+_.*""")

/**
 * Android 包名/进程名：至少两段，可带 `:子进程`。
 * 比单纯 `.*\..*` 更严，避免把 `foo.bar` 类二进制误判为应用。
 */
private val androidPackageProcessRegex =
    Regex("""^[A-Za-z][\w]*(\.[A-Za-z][\w]*)+(:[\w.\-]+)?$""")

private val zygoteOrRuntimeNames = setOf(
    "zygote",
    "zygote64",
    "zygote32",
    "app_process",
    "app_process32",
    "app_process64",
)

/**
 * 进程快照。字段是否填充取决于读取方式：
 * - [io.github.lumkit.tweak.common.utils.ProcessUtils] 列表：cpu / res / swap / name / pid / user / command / cmdline
 * - [io.github.lumkit.tweak.common.utils.ProcessUtils.getProcessDetail]：额外填充 cpuset / cgroup / oom*
 * - [io.github.lumkit.tweak.common.utils.ProcessUtilLite] 列表：cpu / name / command / pid / user
 *
 * Android 应用进程经 [withAppMeta] 后会填充 [friendlyName] / [iconPath]，可直接给 Coil `AsyncImage`。
 *
 * ## 类型判定说明
 * 旧逻辑依赖 `COMMAND` 含 `app_process`。新版 toybox `top` 在 Zygote 子进程上
 * 往往会把 `COMMAND` 显示成已 `setArgV0` 的包名，导致判定失效。
 * 现改为综合：包名形态、沙盒 USER（uN_a* / uN_i*）、是否仍为 app_process/zygote、
 * 以及 [AppsHelper] 已安装列表。
 */
@Immutable
data class ProcessInfo(
    val cpu: Float = 0f,
    val res: Long = 0L,
    val rss: Long = 0L,
    val mem: Long = 0L,
    val swap: Long = 0L,
    /** 进程名；Android 应用多为包名，或 `包名:子进程` */
    val name: String = "",
    val pid: Int = -1,
    val user: String = "",
    val command: String = "",
    val cmdline: String = "",
    /** Linux 进程状态码，如 R/S/D…；详情场景可能用到 */
    val state: String = "",
    val cpuSet: String = "",
    val cGroup: String = "",
    val oomAdj: String = "",
    val oomScore: String = "",
    val oomScoreAdj: String = "",
    /** 展示名：Android 应用为应用标签，否则多为 [name] */
    val friendlyName: String = "",
    /** AppsHelper 图标缓存路径；非 Android 应用进程为空 */
    val iconPath: String = "",
) {
    /** 去掉 `:service` 等后缀后的包名，用于加载图标 / 跳转定位 */
    val appPackageName: String
        get() {
            val fromName = name.substringBefore(':').trim()
            if (looksLikeAndroidPackage(fromName)) {
                return fromName
            }
            val fromCmdline = cmdline
                .substringBefore('\u0000')
                .substringBefore(' ')
                .substringBefore(':')
                .trim()
            if (looksLikeAndroidPackage(fromCmdline)) {
                return fromCmdline
            }
            val fromCommand = command
                .substringAfterLast('/')
                .substringBefore(':')
                .trim()
            if (looksLikeAndroidPackage(fromCommand)) {
                return fromCommand
            }
            return fromName
        }

    /**
     * 是否为 Android 应用进程（可由包名取图标 / 进进程管理过滤）。
     * 不含 zygote / app_process 本体。
     */
    val isAndroidProcess: Boolean
        get() {
            val pkg = appPackageName
            if (pkg.isBlank() || isZygoteOrRuntimeName(pkg) || isZygoteOrRuntimeName(name)) {
                return false
            }

            val packageLike = looksLikeAndroidPackage(pkg) || looksLikeAndroidPackage(name)
            val fromZygote = isFromZygoteRuntime(command)
            val appSandboxUser = isAndroidAppUser(user) || isAndroidIsolatedUser(user)
            val installed = isInstalledPackage(pkg)

            // 新 top：NAME/COMMAND 已是包名，USER 为 uN_a* / uN_i*
            if (packageLike && (appSandboxUser || installed || fromZygote)) {
                return true
            }
            // 旧 top/ps：COMMAND 仍为 app_process*
            if (fromZygote && (packageLike || appSandboxUser || installed)) {
                return true
            }
            // USER 缺失时，靠已安装列表兜底
            if (packageLike && installed) {
                return true
            }
            return false
        }

    /**
     * Android 系统侧应用进程：由 Zygote 拉起，但 USER 不是应用沙盒
     *（如 `system` / `radio` 下的 `com.android.*`）。
     */
    val isSystemProcess: Boolean
        get() = isAndroidProcess && !isAndroidAppUser(user) && !isAndroidIsolatedUser(user)

    /** Android 用户应用进程（含隔离进程） */
    val isAndroidUserProcess: Boolean
        get() = isAndroidProcess && (isAndroidAppUser(user) || isAndroidIsolatedUser(user))

    /** 任意运行在应用沙盒 UID 下的进程（不要求包名形态） */
    val isUserProcess: Boolean
        get() = isAndroidAppUser(user) || isAndroidIsolatedUser(user)

    /** 非 Android 应用进程（原生 / 守护进程等），对应 Scene FILTER_OTHER */
    val isOtherProcess: Boolean
        get() = !isAndroidProcess

    /** UI 优先用友好名 */
    val displayName: String
        get() = friendlyName.ifBlank { name }

    /**
     * 绑定应用元信息：友好名 + 图标缓存路径（对齐 Scene AdapterProcess.loadLabel / loadIcon）。
     */
    fun withAppMeta(
        appLookup: Map<String, Pair<String, String>>? = null,
    ): ProcessInfo {
        if (!isAndroidProcess) {
            return copy(
                friendlyName = name.ifBlank { command },
                iconPath = "",
            )
        }
        val pkg = appPackageName
        val cached = appLookup?.get(pkg)
        if (cached != null) {
            return copy(
                friendlyName = cached.first.ifBlank { pkg },
                iconPath = cached.second,
            )
        }
        val app = AppsHelper.apps.value.find { it.packageName == pkg }
        return copy(
            friendlyName = app?.appName?.takeIf { it.isNotBlank() } ?: pkg,
            iconPath = app?.iconPath?.takeIf { it.isNotBlank() }
                ?: AppsHelper.getIconPath(pkg),
        )
    }
}

@Immutable
data class ThreadInfo(
    val tid: Int = -1,
    val cpuLoad: Double = 0.0,
    val name: String = "",
)

/** 批量填充友好名与图标路径，避免逐条扫 apps 列表 */
fun List<ProcessInfo>.withAppMeta(): List<ProcessInfo> {
    val lookup = AppsHelper.apps.value.associate { app ->
        app.packageName to (app.appName to app.iconPath)
    }
    return map { it.withAppMeta(lookup) }
}

private fun looksLikeAndroidPackage(value: String): Boolean {
    if (value.isBlank() || isZygoteOrRuntimeName(value)) {
        return false
    }
    return androidPackageProcessRegex.matches(value)
}

private fun isZygoteOrRuntimeName(value: String): Boolean {
    val lower = value.lowercase()
    return lower in zygoteOrRuntimeNames || lower.startsWith("app_process")
}

private fun isFromZygoteRuntime(command: String): Boolean {
    if (command.isBlank()) {
        return false
    }
    val lower = command.lowercase()
    return lower.contains("app_process") ||
        lower.endsWith("/zygote") ||
        lower.endsWith("/zygote64") ||
        lower == "zygote" ||
        lower == "zygote64"
}

private fun isAndroidAppUser(user: String): Boolean =
    user.isNotBlank() && androidAppUserRegex.matches(user)

private fun isAndroidIsolatedUser(user: String): Boolean =
    user.isNotBlank() && androidIsolatedUserRegex.matches(user)

private fun isInstalledPackage(pkg: String): Boolean {
    if (pkg.isBlank()) {
        return false
    }
    return AppsHelper.apps.value.any { it.packageName == pkg }
}

/** 供外部过滤：沙盒用户（uN_*） */
fun isAndroidSandboxUser(user: String): Boolean =
    user.isNotBlank() && androidSandboxUserRegex.matches(user)
