package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.FpsUtils.MAX_ACTIVE_METHOD_FAILS
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FpsUtils {
    private val mutex = Mutex()
    private val whitespaceRegex = Regex("\\s+")
    private val surfaceFlingerHexRegex = Regex("\\(([-\uFFFF]{8})\\)")
    /** Qualcomm / OPlus measured_fps：`fps: 59.4 duration:...` */
    private val measuredFpsLabelRegex = Regex("""fps\s*:\s*([\d.]+)""", RegexOption.IGNORE_CASE)

    private var fpsFilePath: String? = null
    private var fpsFileTokenIndex = 0
    private var fpsFileDivider = 1f
    private var persistedFpsSourceLoaded = false
    /** 节点探测已完成且未找到可用源时为 true，避免每帧重复跑慢路径 */
    private var nodeProbeExhausted = false
    private var useSurfaceFlingerPageFlip = true
    private var useSurfaceFlingerLatency = true
    private var useFpsgoStatus = true
    private var useGfxInfo = true
    private var fpsgoStatusPath: String? = null
    /** 与 CpuFrequencyUtil / GpuUtils 一致：按 ro.board.platform 分流，避免跨平台空扫 */
    private var socPlatform: SocPlatform? = null

    /**
     * 一旦读到有效数据就锁定该方法；之后每帧只走这一条，不再扫盘/整链回退。
     * 连续失败 [MAX_ACTIVE_METHOD_FAILS] 次后才解锁重探。
     */
    private var activeMethod = ActiveFpsMethod.Unresolved
    private var activeMethodFailCount = 0
    /** 全量探测失败后冷却，避免每帧 dumpsys / 扫盘 */
    private var discoveryFailCooldown = 0
    private var cachedLatencyPackage: String? = null
    private var cachedLatencyLayer: String? = null

    private var lastFrameTime = -1L
    private var lastFrameCount = -1L

    private const val MAX_ACTIVE_METHOD_FAILS = 5
    private const val DISCOVERY_FAIL_COOLDOWN_FRAMES = 30
    private const val LATENCY_INVALID_MARKER = 11_111_111L

    private enum class SocPlatform {
        MediaTek,
        Qualcomm,
        Other,
    }

    private enum class ActiveFpsMethod {
        Unresolved,
        SysfsNode,
        FpsgoStatus,
        SurfaceFlingerPageFlip,
        SurfaceFlingerLatency,
        GfxInfo,
    }

    /** 联发科 GED 实时节点（不要用 fps_upper_bound） */
    private val mediaTekDirectCandidates = listOf(
        FpsNodeCandidate("/sys/kernel/ged/hal/current_fps"),
        FpsNodeCandidate("/sys/kernel/debug/ged/hal/current_fps"),
        FpsNodeCandidate("/sys/module/ged/parameters/current_fps"),
        FpsNodeCandidate("/proc/ged"),
    )

    /** 高通 / ColorOS DRM 节点 */
    private val qualcommDirectCandidates = listOf(
        FpsNodeCandidate("/sys/class/drm/card0/sde_crtc_fps"),
        FpsNodeCandidate("/sys/class/drm/card0/sde-crtc-0/measured_fps", tokenIndex = 1),
        FpsNodeCandidate("/sys/class/drm/sde-crtc-0/measured_fps", tokenIndex = 1),
        FpsNodeCandidate("/sys/class/drm/sde-crtc-1/measured_fps", tokenIndex = 1),
        FpsNodeCandidate("/sys/class/drm/sde-crtc-2/measured_fps", tokenIndex = 1),
        FpsNodeCandidate("/sys/class/drm/card0-CRTC-0/measured_fps", tokenIndex = 1),
        FpsNodeCandidate("/sys/class/drm/card0-CRTC-1/measured_fps", tokenIndex = 1),
        FpsNodeCandidate("/sys/class/drm/sde-crtc-0/fps", tokenIndex = 0),
        FpsNodeCandidate("/sys/class/graphics/fb0/measured_fps"),
        FpsNodeCandidate("/sys/class/graphics/fb0/fps"),
        FpsNodeCandidate("/sys/kernel/debug/dri/0/state_fps"),
        FpsNodeCandidate("/sys/module/display_fps/parameters/fps"),
        FpsNodeCandidate("/proc/displowpower/fps"),
    )

    private val fpsgoStatusCandidates = listOf(
        "/sys/kernel/debug/fpsgo/fstb/fpsgo_status",
        "/sys/kernel/fpsgo/fstb/fpsgo_status",
        "/sys/kernel/debug/fpsgo/fpsgo_status",
        "/sys/kernel/fpsgo/fpsgo_status",
    )

    private val mediaTekFindRoots = listOf(
        "/sys/kernel/ged",
        "/sys/kernel/debug/ged",
        "/sys/kernel/fpsgo",
        "/sys/kernel/debug/fpsgo",
        "/sys/module/ged",
    )

    private val qualcommFindRoots = listOf(
        "/sys/class/drm",
        "/sys/class/graphics",
        "/sys/kernel/debug/dri",
        "/sys/module/display_fps",
        "/proc/displowpower",
    )

    suspend fun getCurrentFpsText(): String? = mutex.withLock {
        readCurrentFpsUnlocked()?.let { "%.1f".format(it) }
    }

    suspend fun getCurrentFps(): Float = mutex.withLock {
        readCurrentFpsUnlocked() ?: 0f
    }

    suspend fun getCurrentFrameTime(): Int {
        val fps = getCurrentFps()
        if (fps <= 0f) {
            return 0
        }
        return (1000f / fps).toInt()
    }

    suspend fun reset() = mutex.withLock {
        clearCurrentFpsSource()
        nodeProbeExhausted = false
        persistedFpsSourceLoaded = false
        useSurfaceFlingerPageFlip = true
        useSurfaceFlingerLatency = true
        useFpsgoStatus = true
        useGfxInfo = true
        fpsgoStatusPath = null
        unlockActiveMethod()
        // socPlatform 不变，保留缓存
        lastFrameTime = -1L
        lastFrameCount = -1L
        applyPlatformFallbackDefaults(resolveSocPlatform())
    }

    private suspend fun readCurrentFpsUnlocked(): Float? {
        ensurePersistedFpsSourceLoaded()
        resolveSocPlatform()

        // 已锁定：只读这一条路径/方法，绝不扫盘或整链回退
        if (activeMethod != ActiveFpsMethod.Unresolved) {
            return readLockedActiveMethod()
        }

        if (discoveryFailCooldown > 0) {
            discoveryFailCooldown--
            return null
        }

        val fps = discoverAndLockMethod()
        if (fps == null && activeMethod == ActiveFpsMethod.Unresolved) {
            discoveryFailCooldown = DISCOVERY_FAIL_COOLDOWN_FRAMES
        }
        return fps
    }

    private suspend fun readLockedActiveMethod(): Float? {
        val fps = when (activeMethod) {
            ActiveFpsMethod.SysfsNode -> readFpsFromNode()
            ActiveFpsMethod.FpsgoStatus -> readFpsFromFpsgoStatusLocked()
            ActiveFpsMethod.SurfaceFlingerPageFlip -> readFpsFromSurfaceFlingerPageFlip()
            ActiveFpsMethod.SurfaceFlingerLatency -> readFpsFromSurfaceFlingerLatency()
            ActiveFpsMethod.GfxInfo -> readFpsFromGfxInfo()
            ActiveFpsMethod.Unresolved -> null
        }

        if (fps != null && fps > 0f) {
            activeMethodFailCount = 0
            return fps
        }

        // page-flip 首样本为 0 属于正常建基线，保持锁定
        if (activeMethod == ActiveFpsMethod.SurfaceFlingerPageFlip && fps != null && fps >= 0f) {
            return fps
        }

        activeMethodFailCount++
        if (activeMethodFailCount < MAX_ACTIVE_METHOD_FAILS) {
            // 短暂空读（息屏/切应用）不解锁，避免每帧 dumpsys
            return fps
        }

        val clearPersisted = activeMethod == ActiveFpsMethod.SysfsNode
        unlockActiveMethod()
        if (clearPersisted) {
            TweakDataStore.clearFpsSource()
        }
        val rediscovered = discoverAndLockMethod()
        if (rediscovered == null && activeMethod == ActiveFpsMethod.Unresolved) {
            discoveryFailCooldown = DISCOVERY_FAIL_COOLDOWN_FRAMES
        }
        return rediscovered
    }

    private suspend fun discoverAndLockMethod(): Float? {
        // 1) 平台内 sysfs / FPSGO 探测（仅 Unresolved 时执行一次）
        if (!nodeProbeExhausted && fpsFilePath.isNullOrBlank() && fpsgoStatusPath.isNullOrBlank()) {
            detectFpsSource()
            // detect 已锁定（含首次有效读）：直接走锁定读，禁止再整链回退
            if (activeMethod != ActiveFpsMethod.Unresolved) {
                return readLockedActiveMethod()
            }
        }

        if (!fpsFilePath.isNullOrBlank()) {
            readFpsFromNode()?.takeIf { it > 0f }?.let {
                lockActiveMethod(ActiveFpsMethod.SysfsNode)
                return it
            }
        }

        if (useFpsgoStatus && !fpsgoStatusPath.isNullOrBlank()) {
            readFpsFromFpsgoStatusLocked()?.takeIf { it > 0f }?.let {
                lockActiveMethod(ActiveFpsMethod.FpsgoStatus)
                return it
            }
        }

        // 2) 按平台最短回退；成功即锁定
        return when (resolveSocPlatform()) {
            SocPlatform.MediaTek -> discoverMediaTekFallbacks()
            SocPlatform.Qualcomm -> discoverQualcommFallbacks()
            SocPlatform.Other -> {
                discoverMediaTekFallbacks()
                    ?: discoverQualcommFallbacks()
            }
        }
    }

    private suspend fun discoverMediaTekFallbacks(): Float? {
        if (useFpsgoStatus) {
            tryLockMethod(ActiveFpsMethod.FpsgoStatus) {
                readFpsFromFpsgoStatus()
            }?.let { return it }
        }
        if (useSurfaceFlingerLatency) {
            tryLockMethod(ActiveFpsMethod.SurfaceFlingerLatency) {
                readFpsFromSurfaceFlingerLatency()
            }?.let { return it }
        }
        if (useGfxInfo) {
            tryLockMethod(ActiveFpsMethod.GfxInfo) {
                readFpsFromGfxInfo()
            }?.let { return it }
        }
        if (useSurfaceFlingerPageFlip) {
            // 首样本 0 也锁定，避免每帧重新 dumpsys
            tryLockPageFlip()?.let { return it }
        }
        return null
    }

    private suspend fun discoverQualcommFallbacks(): Float? {
        if (useSurfaceFlingerPageFlip) {
            tryLockPageFlip()?.let { return it }
        }
        if (useSurfaceFlingerLatency) {
            tryLockMethod(ActiveFpsMethod.SurfaceFlingerLatency) {
                readFpsFromSurfaceFlingerLatency()
            }?.let { return it }
        }
        if (useGfxInfo) {
            tryLockMethod(ActiveFpsMethod.GfxInfo) {
                readFpsFromGfxInfo()
            }?.let { return it }
        }
        return null
    }

    private suspend fun tryLockPageFlip(): Float? {
        val fps = readFpsFromSurfaceFlingerPageFlip() ?: return null
        if (fps < 0f) {
            return null
        }
        lockActiveMethod(ActiveFpsMethod.SurfaceFlingerPageFlip)
        return fps
    }

    private suspend inline fun tryLockMethod(
        method: ActiveFpsMethod,
        read: suspend () -> Float?,
    ): Float? {
        val fps = read()?.takeIf { it > 0f } ?: return null
        lockActiveMethod(method)
        return fps
    }

    private fun lockActiveMethod(method: ActiveFpsMethod) {
        activeMethod = method
        activeMethodFailCount = 0
        discoveryFailCooldown = 0
        nodeProbeExhausted = true
    }

    private fun unlockActiveMethod() {
        activeMethod = ActiveFpsMethod.Unresolved
        activeMethodFailCount = 0
        discoveryFailCooldown = 0
        nodeProbeExhausted = false
        clearCurrentFpsSource()
        fpsgoStatusPath = null
        cachedLatencyLayer = null
        cachedLatencyPackage = null
    }

    /**
     * 与 [CpuFrequencyUtil] / [GpuUtils] 相同属性源判断平台。
     */
    private suspend fun resolveSocPlatform(): SocPlatform {
        socPlatform?.let { return it }
        val board = KernelProps.getSystemProp("ro.board.platform").lowercase()
        val mtkProp = KernelProps.getSystemProp("ro.mediatek.platform").lowercase()
        val hardware = KernelProps.getSystemProp("ro.hardware").lowercase()
        val resolved = when {
            mtkProp.isNotBlank() ||
                board.startsWith("mt") ||
                hardware.startsWith("mt") ||
                board.contains("mediatek") ||
                hardware.contains("mediatek") -> SocPlatform.MediaTek
            board.startsWith("msm") ||
                board.startsWith("sdm") ||
                board.startsWith("sm") ||
                board.startsWith("qcom") ||
                hardware.contains("qcom") ||
                board.startsWith("kona") ||
                board.startsWith("lahaina") ||
                board.startsWith("taro") ||
                board.startsWith("kalama") ||
                board.startsWith("pineapple") ||
                board.startsWith("sun") ||
                board.startsWith("canoe") -> SocPlatform.Qualcomm
            else -> SocPlatform.Other
        }
        socPlatform = resolved
        applyPlatformFallbackDefaults(resolved)
        return resolved
    }

    private fun applyPlatformFallbackDefaults(platform: SocPlatform) {
        when (platform) {
            SocPlatform.MediaTek -> {
                useFpsgoStatus = true
                // 高通 DRM 探测不会跑；page-flip 在 ColorOS 上常废，默认仍允许但排在后面
            }
            SocPlatform.Qualcomm -> {
                useFpsgoStatus = false
                fpsgoStatusPath = null
            }
            SocPlatform.Other -> Unit
        }
    }

    private fun clearCurrentFpsSource() {
        fpsFilePath = null
        fpsFileTokenIndex = 0
        fpsFileDivider = 1f
    }

    private suspend fun ensurePersistedFpsSourceLoaded() {
        if (persistedFpsSourceLoaded) {
            return
        }
        persistedFpsSourceLoaded = true
        TweakDataStore.fpsSourceFlow().firstOrNull()?.let {
            if (isNonRealtimeFpsPath(it.path)) {
                // 清掉历史误存的 upper_bound / 限帧节点
                return@let
            }
            applyCandidate(
                FpsNodeCandidate(
                    path = it.path,
                    tokenIndex = it.tokenIndex,
                    divider = it.divider,
                )
            )
            lockActiveMethod(ActiveFpsMethod.SysfsNode)
        }
    }

    private suspend fun readFpsFromNode(): Float? {
        val path = fpsFilePath?.takeIf { it.isNotBlank() } ?: return null
        if (isNonRealtimeFpsPath(path)) {
            return null
        }
        val raw = readTextPreferShell(path)
        if (raw.isBlank()) {
            return null
        }
        return parseFpsValue(raw, fpsFileTokenIndex, fpsFileDivider, path)
    }

    private fun parseFpsValue(
        raw: String,
        tokenIndex: Int,
        divider: Float,
        path: String = "",
    ): Float? {
        measuredFpsLabelRegex.find(raw)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { labeled ->
            return normalizeMeasuredFps(labeled / divider.coerceAtLeast(0.0001f), path)
        }

        val token = raw
            .split(whitespaceRegex)
            .getOrNull(tokenIndex)
            ?.replace("fps", "", ignoreCase = true)
            ?.replace("Hz", "", ignoreCase = true)
            ?.trim()
            ?.trim(':')
            ?.toFloatOrNull()

        val value = token
            ?: raw.split(whitespaceRegex)
                .asSequence()
                .mapNotNull { part ->
                    part.replace("fps", "", ignoreCase = true)
                        .replace("Hz", "", ignoreCase = true)
                        .trim()
                        .trim(':')
                        .toFloatOrNull()
                }
                .firstOrNull { it in 1f..480f }

        if (value == null) {
            return null
        }
        return normalizeMeasuredFps(value / divider.coerceAtLeast(0.0001f), path)
    }

    /**
     * Qualcomm measured_fps 内核值常为实际帧率 ×10；若 show 已格式化为 `fps: 59.4` 则不必再除。
     * 这里仅在读到明显偏大的整数刻度时自动缩小。
     */
    private fun normalizeMeasuredFps(value: Float, path: String): Float? {
        if (value <= 0f) {
            return null
        }
        val adjusted = if (
            path.contains("measured_fps", ignoreCase = true) &&
            value > 240f &&
            value <= 2400f
        ) {
            value / 10f
        } else {
            value
        }
        return adjusted.takeIf { it in 0.1f..480f }
    }

    private suspend fun detectFpsSource() {
        when (resolveSocPlatform()) {
            SocPlatform.MediaTek -> detectMediaTekFpsSource()
            SocPlatform.Qualcomm -> detectQualcommFpsSource()
            SocPlatform.Other -> {
                detectMediaTekFpsSource()
                if (activeMethod != ActiveFpsMethod.Unresolved) {
                    return
                }
                detectQualcommFpsSource()
            }
        }
    }

    private suspend fun detectMediaTekFpsSource() {
        resolveFpsgoStatusPath()?.let { path ->
            fpsgoStatusPath = path
            useFpsgoStatus = true
            val fps = parseFpsgoStatus(readTextPreferShell(path))
            if (fps != null && fps > 0f) {
                clearCurrentFpsSource()
                lockActiveMethod(ActiveFpsMethod.FpsgoStatus)
                return
            }
        }

        pickBestCandidate(probeCandidates(mediaTekDirectCandidates))?.let {
            persistCandidate(it)
            lockActiveMethod(ActiveFpsMethod.SysfsNode)
            return
        }

        pickBestCandidate(probeCandidates(discoverMediaTekFpsCandidates()))?.let {
            persistCandidate(it)
            lockActiveMethod(ActiveFpsMethod.SysfsNode)
            return
        }

        pickBestCandidate(
            probeCandidates(
                findFpsCandidatesScoped(mediaTekFindRoots)
                    .filterNot { isNonRealtimeFpsPath(it.path) },
            ),
        )?.let {
            persistCandidate(it)
            lockActiveMethod(ActiveFpsMethod.SysfsNode)
            return
        }

        clearCurrentFpsSource()
        nodeProbeExhausted = true
    }

    private suspend fun detectQualcommFpsSource() {
        useFpsgoStatus = false
        fpsgoStatusPath = null

        pickBestCandidate(probeCandidates(qualcommDirectCandidates))?.let {
            persistCandidate(it)
            lockActiveMethod(ActiveFpsMethod.SysfsNode)
            return
        }

        pickBestCandidate(probeCandidates(discoverDrmFpsCandidates()))?.let {
            persistCandidate(it)
            lockActiveMethod(ActiveFpsMethod.SysfsNode)
            return
        }

        pickBestCandidate(
            probeCandidates(
                findFpsCandidatesScoped(qualcommFindRoots)
                    .filterNot { isNonRealtimeFpsPath(it.path) },
            ),
        )?.let {
            persistCandidate(it)
            lockActiveMethod(ActiveFpsMethod.SysfsNode)
            return
        }

        clearCurrentFpsSource()
        nodeProbeExhausted = true
    }

    private fun isNonRealtimeFpsPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("upper_bound") ||
            lower.contains("fps_periodicity") ||
            lower.contains("switch/fps") ||
            lower.endsWith("/target_fps") ||
            lower.contains("fps_list")
    }

    private suspend fun discoverDrmFpsCandidates(): List<FpsNodeCandidate> {
        val script = """
            for d in /sys/class/drm/*; do
              [ -d "${'$'}d" ] || continue
              for name in measured_fps sde_crtc_fps fps; do
                f="${'$'}d/${'$'}name"
                [ -r "${'$'}f" ] && echo "${'$'}f"
              done
            done
        """.trimIndent()
        return parseDiscoveredFpsPaths(
            runCatching { ReusableShells.execSync(script) }.getOrNull().orEmpty(),
        )
    }

    private suspend fun discoverMediaTekFpsCandidates(): List<FpsNodeCandidate> {
        val script = """
            for d in /sys/kernel/ged/hal /sys/kernel/debug/ged/hal /sys/module/ged/parameters /sys/kernel/fpsgo /sys/kernel/fpsgo/fstb /sys/kernel/debug/fpsgo /sys/kernel/debug/fpsgo/fstb; do
              [ -d "${'$'}d" ] || continue
              for f in "${'$'}d"/*fps* "${'$'}d"/current_fps; do
                [ -r "${'$'}f" ] || continue
                case "${'$'}f" in
                  *upper_bound*|*fps_list*|*periodicity*|*target_fps*) ;;
                  *) echo "${'$'}f" ;;
                esac
              done
            done
        """.trimIndent()
        return parseDiscoveredFpsPaths(
            runCatching { ReusableShells.execSync(script) }.getOrNull().orEmpty(),
        )
    }

    private fun parseDiscoveredFpsPaths(output: String): List<FpsNodeCandidate> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot(::isNonRealtimeFpsPath)
            .distinct()
            .map { path ->
                when {
                    path.contains("fpsgo_status", ignoreCase = true) ->
                        // 表格式，走专用解析，不进入数值节点探测
                        FpsNodeCandidate(path)
                    path.endsWith("measured_fps", ignoreCase = true) ->
                        FpsNodeCandidate(path, tokenIndex = 1)
                    else -> FpsNodeCandidate(path)
                }
            }
            .filterNot { it.path.contains("fpsgo_status", ignoreCase = true) }
            .toList()
    }

    private suspend fun resolveFpsgoStatusPath(): String? {
        // 已缓存路径直接复用，不再每帧验证/枚举
        fpsgoStatusPath?.takeIf { it.isNotBlank() }?.let { return it }
        for (path in fpsgoStatusCandidates) {
            val raw = readTextPreferShell(path)
            if (raw.contains("currentFPS", ignoreCase = true) ||
                raw.contains("current_fps", ignoreCase = true) ||
                raw.lineSequence().any { line ->
                    line.split(whitespaceRegex).any { token ->
                        token.toFloatOrNull()?.let { it in 1f..240f } == true
                    }
                }
            ) {
                fpsgoStatusPath = path
                return path
            }
        }
        val discover = runCatching {
            ReusableShells.execSync(
                """
                for f in /sys/kernel/debug/fpsgo/fstb/fpsgo_status \
                         /sys/kernel/fpsgo/fstb/fpsgo_status \
                         /sys/kernel/debug/fpsgo/*/fpsgo_status \
                         /sys/kernel/fpsgo/*/fpsgo_status; do
                  [ -r "${'$'}f" ] && echo "${'$'}f"
                done
                """.trimIndent(),
            )
        }.getOrNull().orEmpty()
        for (path in discover.lineSequence().map { it.trim() }.filter { it.isNotBlank() }) {
            val raw = readTextPreferShell(path)
            if (raw.isNotBlank()) {
                fpsgoStatusPath = path
                return path
            }
        }
        return null
    }

    private suspend fun readFpsFromFpsgoStatus(): Float? {
        val path = resolveFpsgoStatusPath()
        if (path.isNullOrBlank()) {
            useFpsgoStatus = false
            return null
        }
        return readFpsFromFpsgoStatusLocked()
    }

    /** 锁定后只 cat 已缓存路径，不再枚举候选 */
    private suspend fun readFpsFromFpsgoStatusLocked(): Float? {
        val path = fpsgoStatusPath?.takeIf(String::isNotBlank) ?: return null
        return parseFpsgoStatus(readTextPreferShell(path))
    }

    /**
     * 解析 MTK FPSGO status 表，取非 Bypass 行中最大的 currentFPS。
     * 表头类似：`tid name Bypass ... currentFPS targetFPS ...`
     */
    private fun parseFpsgoStatus(raw: String): Float? {
        if (raw.isBlank()) {
            return null
        }
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val headerIndex = lines.indexOfFirst {
            it.contains("currentFPS", ignoreCase = true) ||
                it.contains("current_fps", ignoreCase = true)
        }
        if (headerIndex < 0) {
            // 无表头时：抓取所有像 FPS 的整数，取最大（排除明显的 pid）
            return lines.asSequence()
                .flatMap { line -> line.split(whitespaceRegex).asSequence() }
                .mapNotNull { it.toFloatOrNull() }
                .filter { it in 1f..240f }
                .maxOrNull()
        }
        val headers = lines[headerIndex].split(whitespaceRegex).filter { it.isNotBlank() }
        val fpsIndex = headers.indexOfFirst {
            it.equals("currentFPS", ignoreCase = true) ||
                it.equals("current_fps", ignoreCase = true)
        }
        val bypassIndex = headers.indexOfFirst { it.equals("Bypass", ignoreCase = true) }
        var best = 0f
        for (i in (headerIndex + 1) until lines.size) {
            val line = lines[i]
            if (line.startsWith("FTEH", ignoreCase = true)) {
                break
            }
            val cols = line.split(whitespaceRegex).filter { it.isNotBlank() }
            if (bypassIndex >= 0 && cols.getOrNull(bypassIndex).equals("y", ignoreCase = true)) {
                continue
            }
            val fps = if (fpsIndex >= 0) {
                cols.getOrNull(fpsIndex)?.toFloatOrNull()
            } else {
                cols.mapNotNull { it.toFloatOrNull() }.firstOrNull { it in 1f..240f }
            } ?: continue
            if (fps > best && fps <= 240f) {
                best = fps
            }
        }
        return best.takeIf { it > 0f }
    }

    private suspend fun readTextPreferShell(path: String): String {
        val viaFiles = Files.readText(path).getOrNull()?.trim().orEmpty()
        if (viaFiles.isNotBlank()) {
            return viaFiles
        }
        return runCatching {
            ReusableShells.execSync("cat ${shellSingleQuote(path)} 2>/dev/null")
        }.getOrNull()?.trim().orEmpty()
    }

    private suspend fun readFpsFromGfxInfo(): Float? {
        val packageName = ForegroundAppMonitor.currentForegroundPackage?.takeIf(String::isNotBlank)
            ?: return null
        val output = runCatching {
            ReusableShells.execSync("dumpsys gfxinfo ${shellSingleQuote(packageName)} framestats")
        }.getOrNull().orEmpty()
        if (output.isBlank()) {
            return null
        }
        if (output.contains("No process found", ignoreCase = true) ||
            output.contains("Could not find", ignoreCase = true)
        ) {
            return null
        }
        return parseGfxInfoFramestats(output)
    }

    /**
     * 从 gfxinfo PROFILEDATA 的 FrameCompleted 时间戳估算近 1 秒 FPS。
     * 列定义随 Android 版本略有差异，优先找含 FRAME_COMPLETED 的列。
     */
    private fun parseGfxInfoFramestats(output: String): Float? {
        val lines = output.lineSequence().map { it.trimEnd() }.toList()
        val headerIndex = lines.indexOfFirst { it.startsWith("---PROFILEDATA---") }
        if (headerIndex < 0 || headerIndex + 1 >= lines.size) {
            return null
        }
        val columns = lines[headerIndex + 1].split(',').map { it.trim() }
        var completedIndex = columns.indexOfFirst {
            it.contains("FRAME_COMPLETED", ignoreCase = true) ||
                it.equals("FrameCompleted", ignoreCase = true)
        }
        if (completedIndex < 0) {
            // 常见旧格式：IntendedVsync, Vsync, OldestInputEvent, NewestInputEvent, HandleInputStart,
            // AnimationStart, PerformTraversalsStart, DrawStart, SyncQueued, SyncStart, IssueDrawCommandsStart,
            // SwapBuffers, FrameCompleted
            completedIndex = 12
        }
        val timestamps = ArrayList<Long>()
        for (i in headerIndex + 2 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("---") || line.isBlank()) {
                break
            }
            val cols = line.split(',')
            val ts = cols.getOrNull(completedIndex)?.trim()?.toLongOrNull() ?: continue
            if (ts > 0L) {
                timestamps += ts
            }
        }
        if (timestamps.size < 2) {
            return null
        }
        val newest = timestamps.last()
        val count = timestamps.count { it >= newest - 1_000_000_000L }
        return count.toFloat().takeIf { it > 0f }
    }

    private data class ProbedCandidate(
        val candidate: FpsNodeCandidate,
        val fps: Float,
    )

    /**
     * 用单次 shell 批量 `[ -r ] && cat`，把 N 次 Shizuku Binder 往返压成 1 次。
     */
    private suspend fun probeCandidates(
        candidates: List<FpsNodeCandidate>,
    ): List<ProbedCandidate> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val byPath = LinkedHashMap<String, FpsNodeCandidate>(candidates.size)
        candidates
            .filterNot { isNonRealtimeFpsPath(it.path) }
            .forEach { byPath.putIfAbsent(it.path, it) }
        if (byPath.isEmpty()) {
            return emptyList()
        }

        val pathsArg = byPath.keys.joinToString(" ") { shellSingleQuote(it) }
        val script = """
            for p in $pathsArg; do
              if [ -r "${'$'}p" ]; then
                echo "__FPS_PATH__${'$'}p"
                # cat|tr：避免 tr 重定向失败时吃掉 ReusableShell stdin 挂死
                cat "${'$'}p" 2>/dev/null | tr '\n' ' ' 2>/dev/null
                echo
                echo "__FPS_END__"
              fi
            done
        """.trimIndent()

        val output = runCatching { ReusableShells.execSync(script) }.getOrNull().orEmpty()
        if (output.isBlank()) {
            return emptyList()
        }

        val probed = mutableListOf<ProbedCandidate>()
        var currentPath: String? = null
        val content = StringBuilder()
        for (line in output.lineSequence()) {
            when {
                line.startsWith("__FPS_PATH__") -> {
                    currentPath = line.removePrefix("__FPS_PATH__").trim()
                    content.clear()
                }
                line == "__FPS_END__" -> {
                    val path = currentPath
                    currentPath = null
                    if (path.isNullOrBlank()) continue
                    val candidate = byPath[path] ?: continue
                    val fps = parseFpsValue(
                        raw = content.toString().trim(),
                        tokenIndex = candidate.tokenIndex,
                        divider = candidate.divider,
                        path = candidate.path,
                    ) ?: continue
                    if (fps > 0f) {
                        probed += ProbedCandidate(candidate, fps)
                    }
                }
                else -> {
                    if (currentPath != null) {
                        if (content.isNotEmpty()) content.append(' ')
                        content.append(line.trim())
                    }
                }
            }
        }
        return probed
    }

    /** 多 CRTC 时优先选当前读数更高的主屏节点（一加外接/虚拟 CRTC 常为 0） */
    private fun pickBestCandidate(probed: List<ProbedCandidate>): FpsNodeCandidate? {
        return probed.maxByOrNull { it.fps }?.candidate
    }

    private suspend fun persistCandidate(candidate: FpsNodeCandidate) {
        applyCandidate(candidate)
        TweakDataStore.setFpsSource(
            FpsSourceConfig(
                path = candidate.path,
                tokenIndex = candidate.tokenIndex,
                divider = candidate.divider,
            )
        )
    }

    private suspend fun findFpsCandidatesScoped(
        roots: List<String>,
    ): List<FpsNodeCandidate> {
        if (roots.isEmpty()) {
            return emptyList()
        }
        val rootsArg = roots.joinToString(" ") { shellSingleQuote(it) }
        val script = """
            for root in $rootsArg; do
              [ -d "${'$'}root" ] || continue
              find "${'$'}root" \( -name measured_fps -o -name fps -o -name current_fps -o -name sde_crtc_fps \) 2>/dev/null
            done
        """.trimIndent()

        val output = runCatching { ReusableShells.execSync(script) }.getOrNull().orEmpty()
        if (output.isBlank()) {
            return emptyList()
        }

        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot(::isNonRealtimeFpsPath)
            .distinct()
            .map { path ->
                when {
                    path.contains("measured_fps", ignoreCase = true) ->
                        FpsNodeCandidate(path, tokenIndex = 1)
                    else -> FpsNodeCandidate(path)
                }
            }
            .toList()
    }

    private fun applyCandidate(candidate: FpsNodeCandidate) {
        fpsFilePath = candidate.path
        fpsFileTokenIndex = candidate.tokenIndex
        fpsFileDivider = candidate.divider
    }

    private fun shellSingleQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private suspend fun readFpsFromSurfaceFlingerPageFlip(): Float? {
        val result = runCatching {
            ReusableShells.execSync("service call SurfaceFlinger 1013")
        }.getOrNull()?.trim().orEmpty()

        if (result.isBlank() || result == "error" || !result.contains("Parcel")) {
            useSurfaceFlingerPageFlip = false
            return null
        }

        val hex = surfaceFlingerHexRegex
            .find(result)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(".", "")

        val frameCount = hex?.toLongOrNull(16) ?: run {
            val startIndex = result.indexOf('(')
            if (startIndex < 0 || startIndex + 9 > result.length) {
                return null
            }
            result.substring(startIndex + 1, startIndex + 9).toLongOrNull(16)
        } ?: return null

        val now = System.currentTimeMillis()
        val fps = if (lastFrameTime > 0L && lastFrameCount >= 0L && now > lastFrameTime) {
            (frameCount - lastFrameCount) * 1000f / (now - lastFrameTime)
        } else {
            0f
        }

        lastFrameCount = frameCount
        lastFrameTime = now
        return fps.takeIf { it >= 0f }
    }

    /**
     * ColorOS 常禁用/改码 `service call SurfaceFlinger 1013`。
     * 回退到 `dumpsys SurfaceFlinger --latency <layer>`，按近 1s present 时间戳计数。
     */
    private suspend fun readFpsFromSurfaceFlingerLatency(): Float? {
        val packageName = ForegroundAppMonitor.currentForegroundPackage?.takeIf(String::isNotBlank)
        val layer = resolveLatencyLayer(packageName)
        if (layer.isNullOrBlank()) {
            // 无前台包名时仍尝试全局 latency（部分版本支持）
            val global = runCatching {
                ReusableShells.execSync("dumpsys SurfaceFlinger --latency")
            }.getOrNull().orEmpty()
            computeFpsFromLatency(global)?.let { return it }
            return null
        }

        val output = runCatching {
            ReusableShells.execSync(
                "dumpsys SurfaceFlinger --latency ${shellSingleQuote(layer)}"
            )
        }.getOrNull().orEmpty()

        val fps = computeFpsFromLatency(output)
        if (fps == null && output.contains(LATENCY_INVALID_MARKER.toString())) {
            // 图层失效：清缓存，下次（或解锁后）重新 --list
            cachedLatencyLayer = null
            cachedLatencyPackage = null
            return null
        }
        return fps
    }

    private suspend fun resolveLatencyLayer(packageName: String?): String? {
        if (packageName.isNullOrBlank()) {
            return null
        }
        if (packageName == cachedLatencyPackage && !cachedLatencyLayer.isNullOrBlank()) {
            return cachedLatencyLayer
        }
        val list = runCatching {
            ReusableShells.execSync("dumpsys SurfaceFlinger --list")
        }.getOrNull().orEmpty()
        if (list.isBlank()) {
            useSurfaceFlingerLatency = false
            return null
        }

        val layers = list.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.startsWith("RequestedLayerState{", ignoreCase = true) ||
                    it.contains("Bounds for", ignoreCase = true) ||
                    it.contains("Input Consumer", ignoreCase = true)
            }
            .map { line ->
                // Android 15 list 可能包一层 RequestedLayerState{name#id ...}
                Regex("""RequestedLayerState\{([^#}]+)""")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?: line
            }
            .filter { it.contains(packageName) }
            .toList()

        val layer = layers.firstOrNull { it.contains("SurfaceView", ignoreCase = true) }
            ?: layers.firstOrNull { it.contains("BLAST", ignoreCase = true) }
            ?: layers.firstOrNull()
        cachedLatencyPackage = packageName
        cachedLatencyLayer = layer
        return layer
    }

    private fun computeFpsFromLatency(output: String): Float? {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.size < 3) {
            return null
        }

        val refreshPeriodNs = lines[0].substringBefore('\t').trim().toLongOrNull() ?: return null
        if (refreshPeriodNs <= 0L || refreshPeriodNs == LATENCY_INVALID_MARKER) {
            return null
        }

        val presentTimes = ArrayList<Long>(lines.size)
        for (index in 1 until lines.size) {
            val cols = lines[index].split(whitespaceRegex)
            if (cols.size < 2) continue
            // B: vsync / ready；部分机型 C 更准，优先取非 0 的最右有效列
            val present = cols.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0L }
                ?: cols.getOrNull(1)?.toLongOrNull()
                ?: continue
            if (present <= 0L || present >= Long.MAX_VALUE / 2) continue
            presentTimes += present
        }
        if (presentTimes.size < 2) {
            return null
        }

        val newest = presentTimes.last()
        val windowNs = 1_000_000_000L
        val count = presentTimes.count { it >= newest - windowNs }
        return count.toFloat().takeIf { it > 0f }
    }

    private data class FpsNodeCandidate(
        val path: String,
        val tokenIndex: Int = 0,
        val divider: Float = 1f,
    )
}
