package io.github.lumkit.tweak.common.utils.fps

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

object SurfaceFlingerFpsUtil {
    private val mutex = Mutex()

    private const val TAG = "SurfaceFlingerFpsUtil"

    private const val FOCUS_PREFIX = "mCurrentFocus=Window{"
    private const val FOCUS_SUFFIX = "}"
    private const val LAYER_V2_PREFIX = "RequestedLayerState{"
    private const val LAYER_V2_SUFFIX = " parentId="
    private const val FPS_CACHE_WINDOW_MS = 500L
    private val SPACE_REGEX = Regex("\\s+")
    private var LASTED_FRAME_TIME = 0L
    private var LASTED_FRAME_LAYER = ""
    private var LASTED_FPS = -0f
    private var LASTED_FPS_TIMESTAMP = 0L

    /**
     * 获取当前窗口焦点
     */
    private suspend fun getCurrentFocus(): String? {
        val tick = Clock.System.now().toEpochMilliseconds()
        val focus = ReusableShells.execSync("dumpsys window | grep \"mCurrentFocus\"").takeIf {
            it.contains(FOCUS_PREFIX)
        }?.let { result ->
            val bean = runCatching {
                result.substring(
                    result.indexOf(FOCUS_PREFIX) + FOCUS_PREFIX.length,
                    result.lastIndexOf(FOCUS_SUFFIX)
                )
            }.getOrNull()
            bean?.split(SPACE_REGEX)?.lastOrNull()
        }
        val time = Clock.System.now().toEpochMilliseconds() - tick
        logD("time: ${time}ms, focus: $focus", TAG)
        return focus
    }

    private suspend fun layerList(focus: String): List<String> {
        val tick = Clock.System.now().toEpochMilliseconds()
        val layerList = ReusableShells.execSync("dumpsys SurfaceFlinger --list | grep \"$focus\"")
            .split("\n")
            .map { parseLayer(it.trim()) ?: "" }
            .filter { it.isNotEmpty() }
        val time = Clock.System.now().toEpochMilliseconds() - tick
        logD("time: ${time}ms, layerList: $layerList", TAG)
        return layerList
    }

    private fun parseLayer(info: String): String? {
        val isV2LayerInfo = info.startsWith(LAYER_V2_PREFIX)
        return when {
            isV2LayerInfo -> parseLayerV2(info)
            else -> info
        }
    }

    private fun parseLayerV2(info: String): String? {
        return runCatching {
            info.substring(
                info.indexOf(LAYER_V2_PREFIX) + LAYER_V2_PREFIX.length,
                info.lastIndexOf(LAYER_V2_SUFFIX)
            )
        }.getOrNull()
    }

    private fun getActiveLayer(layerList: List<String>): String? {
        val tick = Clock.System.now().toEpochMilliseconds()
        val hasVideoLayer = hasVideoLayer(layerList)
        val activeLayer = when {
            hasVideoLayer -> {
                val blasts = layerList.filter {
                    it.contains("SurfaceView[") && it.contains("](")
                }
                logD("blasts: $blasts", TAG)

                val lastedIndexList = blasts.map {
                    it.substring(it.lastIndexOf("#") + 1).trim().toIntOrNull() ?: -1
                }
                logD("lastedIndex: $lastedIndexList", TAG)
                blasts.getOrNull(lastedIndexList.withIndex().maxByOrNull { it.value }?.index ?: blasts.lastIndex)
            }

            else -> {
                val cleanLayers = layerList.filter {
                    !it.contains(SPACE_REGEX)
                }
                val lastedIndexList = cleanLayers.map {
                    it.substring(it.lastIndexOf("#") + 1).trim().toIntOrNull() ?: -1
                }
                cleanLayers.getOrNull(lastedIndexList.withIndex().maxByOrNull { it.value }?.index ?: cleanLayers.lastIndex) ?: run {
                    val maxIndex = layerList.map {
                        it.substring(it.lastIndexOf("#") + 1).trim().toIntOrNull() ?: -1
                    }
                    layerList.getOrNull(maxIndex.withIndex().maxByOrNull { it.value }?.index ?: layerList.lastIndex)
                }
            }
        }
        val time = Clock.System.now().toEpochMilliseconds() - tick
        logD("time: ${time}ms, activeLayer: $activeLayer", TAG)
        return activeLayer
    }

    private fun hasVideoLayer(layerList: List<String>): Boolean {
        return layerList.any { it.contains("](") && it.contains("SurfaceView[") }
    }

    private suspend fun latency(layer: String): Float {
        val tick = Clock.System.now().toEpochMilliseconds()

        val result = ReusableShells.execSync("dumpsys SurfaceFlinger --latency \"$layer\"")
        logD("latency: $result", TAG)

        val fps = calculateFps(result)

        val time = Clock.System.now().toEpochMilliseconds() - tick
        logD("time: ${time}ms, fps: $fps", TAG)
        return fps
    }

    private fun calculateFps(
        latencyOutput: String
    ): Float {
        val buffer = latencyOutput.lineSequence()
            .map {
                it.trim().split(SPACE_REGEX).map { row ->
                    row.toLongOrNull()?.takeIf { take ->
                        take in 0..Long.MAX_VALUE
                    } ?: 0L
                }
            }.filter { it.size == 3 }
            .map { it.getOrNull(2) ?: -1L }
            .filter { it >= LASTED_FRAME_TIME }
            .toList()

        logD("buffer: $buffer", TAG)

        if (buffer.isEmpty()) {
            return -0f
        }

        val start = if (buffer.size == 1) {
            LASTED_FRAME_TIME
        } else {
            buffer.first()
        }
        val end = buffer.last()
        val durationNs = end - start
        logD("lasted frame time: $end ns", TAG)

        if (durationNs <= 0) {
            return 0f
        }

        return (buffer.size * 1_000_000_000f / durationNs).also {
            LASTED_FRAME_TIME = end
        }
    }

    suspend fun getCurrentFps(): Float = mutex.withLock {
        val tick = Clock.System.now().toEpochMilliseconds()
        if (tick - LASTED_FPS_TIMESTAMP in 0..FPS_CACHE_WINDOW_MS) {
            return LASTED_FPS
        }

        val focus = getCurrentFocus()
        if (focus == null) {
            logD("focus is null", TAG)
            return LASTED_FPS
        }

        val layerList = layerList(focus)
        val activeLayer = getActiveLayer(layerList)
        if (activeLayer == null) {
            logD("activeLayer is null", TAG)
            return LASTED_FPS
        }
        if (activeLayer != LASTED_FRAME_LAYER) {
            LASTED_FRAME_TIME = 0L
        }
        LASTED_FRAME_LAYER = activeLayer

        val fps = latency(activeLayer)
        val time = Clock.System.now().toEpochMilliseconds() - tick
        logD("total time: ${time}ms, fps: $fps", TAG)
        LASTED_FPS = fps
        LASTED_FPS_TIMESTAMP = tick
        return LASTED_FPS
    }
}
