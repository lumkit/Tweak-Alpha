package io.github.lumkit.tweak.common.utils.fps

/**
 * SurfaceFlinger 延迟采样。仅在内核 fps 节点 / SF 1013 不可用时作为回退。
 */
expect object SurfaceFlingerFpsUtil {
    suspend fun getCurrentFps(): Float
}
