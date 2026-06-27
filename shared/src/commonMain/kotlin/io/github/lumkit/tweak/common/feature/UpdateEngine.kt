package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.common.shell.ReusableShells

class UpdateEngine {

    private val shell by lazy {
        ReusableShells.getInstance(
            "update_engine_client",
            redirectErrorStream = true,
        )
    }

    fun interface UpdateFollowListener {
        fun onLog(log: String)
    }

    private var updateFollowListener: UpdateFollowListener? = null

    @Throws
    suspend fun installRom(
        binPath: String,
        propertiesPath: String,
    ) {
        val cmd = listOf(
            "update_engine_client",
            "--update",
            "--payload=file://$binPath",
            "--headers=file://$propertiesPath"
        )
        ReusableShells.execSync(cmd)
    }

    suspend fun follow() {
        shell.doCmdWithListener("update_engine_client --follow") {
            updateFollowListener?.onLog(it)
        }
    }

    suspend fun cancel() {
        ReusableShells.execSync("update_engine_client --cancel")
    }

    suspend fun resetStatus() {
        ReusableShells.execSync("update_engine_client --reset_status")
    }

    suspend fun merge() {
        ReusableShells.execSync("update_engine_client --merge")
    }

    fun setUpdateFollowListener(listener: UpdateFollowListener) {
        this.updateFollowListener = listener
    }


}