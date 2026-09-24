package io.github.lumkit.tweak.common.utils.fps

import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.SamplingSettingsStore
import io.github.lumkit.tweak.server.fps.SurfaceFlingerFrameSampler
import io.github.lumkit.tweak.server.ipc.TweakServerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * App 侧入口：优先走 TweakServer Binder（特权进程内 dumpAsync），
 * Daemon 未连接时才在本进程 dumpsys 回退。
 */
actual object SurfaceFlingerFpsUtil {

    private val localSampler by lazy {
        SurfaceFlingerFrameSampler(
            shellExec = { cmd -> runBlocking { ReusableShells.execSync(cmd) } },
        )
    }

    actual suspend fun getCurrentFps(): Float = withContext(Dispatchers.IO) {
        val source = SamplingSettingsStore.sfLatencySource.value
        val remote = TweakServerConnection.service
        if (remote != null) {
            val fps = runCatching { remote.currentFps(source) }.getOrNull()
            if (fps != null) return@withContext fps
        }
        localSampler.currentFps(source)
    }
}
