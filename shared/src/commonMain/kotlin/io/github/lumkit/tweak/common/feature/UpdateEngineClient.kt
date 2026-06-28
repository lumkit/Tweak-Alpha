package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.common.Const
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.joinPath
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class UpdateEngineClient {
    companion object {

        private const val TAG = "UpdateEngineClient"

        /**
         * 是否支持OTA更新
         */
        suspend fun support(): Boolean {
            val result = ReusableShells.execSync("ls /dev/block/bootdevice/by-name")
            return result.contains("_a|_b".toRegex()) && Files.exists("/system/bin/update_engine_client").getOrNull() ?: false
        }

        suspend fun unzipRom(romPath: String): String {
            val entry = ReusableShells.execSync("unzip -l $romPath")
            if (!entry.contains("payload\\.bin|payload_properties\\.txt".toRegex())) {
                throw RuntimeException("ROM不完整，请重新选择文件！")
            }

            val cachePath = Const.Path.otaPackage

            ReusableShells.execSync(
                "rm -rf ${cachePath}/*",
                "unzip $romPath -d $cachePath",
                "chmod -R 777 $cachePath"
            )
            return cachePath
        }

        suspend fun installRom(dir: String) {
            val bin = dir joinPath "payload.bin"
            val property = dir joinPath "payload_properties.txt"
            val propertyContent = Files.readText(property)
                .getOrNull() ?: ""

            logD("propertyContent = $propertyContent")

            val cmd = """
                update_engine_client --update \
                --payload=file://$bin \
                --headers='${propertyContent}'
            """.trimIndent()

            ReusableShells.execSync(cmd)
        }

        suspend fun cancel() {
            ReusableShells.execSync("update_engine_client --cancel")
        }

        suspend fun merge() {
            ReusableShells.execSync("update_engine_client --merge")
        }

        suspend fun reset() {
            ReusableShells.execSync("update_engine_client --reset_status")
        }

        suspend fun suspend() {
            ReusableShells.execSync("update_engine_client --suspend")
        }

        suspend fun resume() {
            ReusableShells.execSync("update_engine_client --resume")
        }
    }

    private val reusableShell = ReusableShells.getInstance(
        "update_engine_client",
        redirectErrorStream = true,
    )
    private val updateEnginClientFilePath = "/system/bin/update_engine_client"

    fun interface OnReadLineListener {
        fun line(line: String)
    }

    private var listener: OnReadLineListener? = null

    fun setOnReadLineListener(listener: OnReadLineListener) {
        this.listener = listener
    }

    fun removeOnReadLineListener() {
        listener = null
    }

    /**
     * 监听update_engine_client的action
     */
    fun watch() {
        reusableShell.setOnReadLineListener { line ->
            try {
                listener?.line(line)
            }catch (e: Exception) {
                logE(e.stackTraceToString())
            }
        }
    }

    /**
     * 跟踪更新状态
     */
    suspend fun follow() = withContext(Dispatchers.IO) {
        while (isActive) {
            if (Files.exists(updateEnginClientFilePath).getOrNull() ?: false) {
                reusableShell.commitCmdSync("update_engine_client --follow")
            }
        }
    }

    private fun Pair<String, String>.infoCode(): Int {
        val cc = first
        if (!cc.contains("update_engine_client_android")) return 0
        val info = cc.substring(cc.indexOf("[") + 1, cc.indexOf("]"))
        return info.substring(info.indexOf("(") + 1, info.indexOf(")")).trim().toIntOrNull() ?: -1
    }

    private fun String.info(): Pair<String, String> = run {
        substring(0, indexOf(" ")) to substring(indexOf(" ") + 1)
    }

    private fun Pair<String, String>.onPayloadApplicationComplete(): Int {
        return second.substring(second.length - 3).toIntOrNull() ?: -1
    }

    private fun Pair<String, String>.commandTook(): Long =
        second.trim().split("\\s+".toRegex())[2].toLongOrNull() ?: 0
}