package io.github.lumkit.tweak.common.utils.fps

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FpsUtils {
    private val mutex = Mutex()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val DEFAULT_SUBSTRING_COMMAND = "| awk '{print \\$2}'"
    private const val SURFACE_FLINGER_FRAME_COUNT_COMMAND = "service call SurfaceFlinger 1013"

    private var fpsFilePath: String? = null
    private var subStrCommand = DEFAULT_SUBSTRING_COMMAND
    private var fpsCommand2 = SURFACE_FLINGER_FRAME_COUNT_COMMAND
    private var lastTime = -1L
    private var lastFrames = -1L
    private var isFindingFpsNode = false

    suspend fun getCurrentFpsText(): String? = mutex.withLock {
        readCurrentFpsTextLocked()
    }

    private var usedSurfaceFlingerFps = false
    suspend fun getCurrentFps(): Float = mutex.withLock {
        if (usedSurfaceFlingerFps) {
            return SurfaceFlingerFpsUtil.getCurrentFps()
        }

        readCurrentFpsTextLocked()?.toFloatOrNull()?.also {
            usedSurfaceFlingerFps = false
        } ?: run {
            usedSurfaceFlingerFps = true
            SurfaceFlingerFpsUtil.getCurrentFps()
        }
    }

    suspend fun getCurrentFrameTime(): Int {
        val fps = getCurrentFps()
        if (fps <= 0f) {
            return 0
        }
        return (1000f / fps).toInt()
    }

    suspend fun reset() = mutex.withLock {
        fpsFilePath = null
        subStrCommand = DEFAULT_SUBSTRING_COMMAND
        fpsCommand2 = SURFACE_FLINGER_FRAME_COUNT_COMMAND
        lastTime = -1L
        lastFrames = -1L
        isFindingFpsNode = false
    }

    private suspend fun readCurrentFpsTextLocked(): String? {
        fpsFilePath?.takeIf { it.isNotBlank() }?.let { path ->
            readFpsNode(path)?.let { return it }
            fpsFilePath = null
            subStrCommand = DEFAULT_SUBSTRING_COMMAND
        }

        if (fpsFilePath == null) {
            when {
                fileExists("/sys/class/drm/sde-crtc-0/measured_fps") -> {
                    fpsFilePath = "/sys/class/drm/sde-crtc-0/measured_fps"
                    subStrCommand = DEFAULT_SUBSTRING_COMMAND
                    readFpsNode(fpsFilePath.orEmpty())?.let { return it }
                }

                fileExists("/sys/class/graphics/fb0/measured_fps") -> {
                    fpsFilePath = "/sys/class/graphics/fb0/measured_fps"
                    subStrCommand = ""
                    readFpsNode(fpsFilePath.orEmpty())?.let { return it }
                }

                else -> {
                    fpsFilePath = ""
                    launchFpsNodeDiscovery()
                }
            }
        }

        if (fpsCommand2.isNotEmpty()) {
            val result = ReusableShells.execSync(fpsCommand2).trim()
            if (result.isBlank() || result == "error" || !result.contains("Parcel")) {
                fpsCommand2 = ""
            } else {
                try {
                    val index = result.indexOf('(')
                    if (index >= 0 && index + 9 <= result.length) {
                        val frames = result.substring(index + 1, index + 9).toLong(16)
                        val time = currentTimeMillis()
                        var fps = 0f
                        if (lastTime > 0L && lastFrames >= 0L && time > lastTime) {
                            fps = (frames - lastFrames) * 1000f / (time - lastTime)
                        }
                        lastFrames = frames
                        lastTime = time
                        return if (fps >= 0f) {
                            "%.1f".format(fps)
                        } else {
                            null
                        }
                    }
                } catch (_: Exception) {
                    if (!(lastTime > 0L && lastFrames >= 0L)) {
                        fpsCommand2 = ""
                    }
                }
            }
        }

        return null
    }

    private suspend fun readFpsNode(path: String): String? {
        val viaFiles = Files.readText(path).getOrNull().orEmpty().trim()
        val raw = if (viaFiles.isNotBlank()) {
            viaFiles
        } else {
            val command = buildString {
                append("cat ")
                append(shellSingleQuote(path))
                if (subStrCommand.isNotBlank()) {
                    append(' ')
                    append(subStrCommand)
                }
                append(" 2>/dev/null")
            }
            ReusableShells.execSync(command).trim()
        }
        return parseNodeFps(raw)
    }

    private fun parseNodeFps(raw: String): String? {
        if (raw.isBlank()) {
            return null
        }
        val normalized = raw.lineSequence()
            .flatMap { it.trim().split(Regex("\\s+")).asSequence() }
            .map { token -> token.trim().trim(':').replace("fps", "", ignoreCase = true) }
            .firstOrNull { it.toFloatOrNull() != null }
            ?: raw.trim().takeIf { it.toFloatOrNull() != null }
            ?: return null
        val value = normalized.toFloatOrNull() ?: return null
        if (value <= 0f) {
            return null
        }
        val adjusted = if (subStrCommand.isNotBlank() && value > 240f && value <= 2400f) {
            value / 10f
        } else {
            value
        }
        return if (adjusted > 0f) {
            "%.1f".format(adjusted)
        } else {
            null
        }
    }

    private fun launchFpsNodeDiscovery() {
        if (isFindingFpsNode) {
            return
        }
        isFindingFpsNode = true
        bgScope.launch {
            try {
                val measured = discoverFpsPath("measured_fps")
                val fps = measured ?: discoverFpsPath("fps")
                mutex.withLock {
                    fpsFilePath = fps.orEmpty()
                    subStrCommand = if (fpsFilePath?.contains("/sys/class/graphics/fb0/measured_fps") == true) {
                        ""
                    } else {
                        DEFAULT_SUBSTRING_COMMAND
                    }
                    isFindingFpsNode = false
                }
            } catch (_: Exception) {
                mutex.withLock {
                    fpsFilePath = ""
                    subStrCommand = DEFAULT_SUBSTRING_COMMAND
                    isFindingFpsNode = false
                }
            }
        }
    }

    private suspend fun discoverFpsPath(name: String): String? {
        return ReusableShells.execSync("find /sys -name $name 2>/dev/null")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.contains("crtc") }
            .minOrNull()
    }

    private suspend fun fileExists(path: String): Boolean {
        return Files.exists(path).getOrNull() == true
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()

    private fun shellSingleQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}