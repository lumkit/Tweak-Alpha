package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.common.Const
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.KernelProps
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

        private val updateEngineClientPaths = listOf(
            "/system/bin/update_engine_client",
            "/system_ext/bin/update_engine_client",
            "/vendor/bin/update_engine_client",
        )

        /**
         * 是否支持 payload.bin OTA（A/B + update_engine_client）。
         *
         * 不依赖 Native 文件服务，也不只扫高通的 bootdevice 分区目录。
         */
        suspend fun support(): Boolean {
            return isAbUpdateDevice() && hasUpdateEngineClient()
        }

        private suspend fun isAbUpdateDevice(): Boolean {
            if (KernelProps.getSystemProp("ro.build.ab_update").equals("true", ignoreCase = true)) {
                return true
            }
            val slotSuffix = KernelProps.getSystemProp("ro.boot.slot_suffix").trim()
            if (slotSuffix.contains("_a", ignoreCase = true) ||
                slotSuffix.contains("_b", ignoreCase = true) ||
                slotSuffix.equals("a", ignoreCase = true) ||
                slotSuffix.equals("b", ignoreCase = true)
            ) {
                return true
            }
            val slot = KernelProps.getSystemProp("ro.boot.slot").trim()
            if (slot.equals("a", ignoreCase = true) || slot.equals("b", ignoreCase = true)) {
                return true
            }
            val byName = ReusableShells.execSync(
                "ls /dev/block/by-name /dev/block/bootdevice/by-name 2>/dev/null"
            )
            return byName.contains("_a") || byName.contains("_b")
        }

        private suspend fun hasUpdateEngineClient(): Boolean {
            val output = ReusableShells.execSync(
                buildString {
                    append("command -v update_engine_client 2>/dev/null; ")
                    append("ls ")
                    append(updateEngineClientPaths.joinToString(" "))
                    append(" 2>/dev/null")
                }
            )
            return output.contains("update_engine_client")
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

        suspend fun unzipRomFromUri(romUri: String): String {
            val cachePath = Const.Path.otaPackage
            Files.unzipFromUri(romUri, cachePath)
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

        private val taskShell = ReusableShells.getInstance(
            "update_engine_client_task",
            redirectErrorStream = true,
        )

        suspend fun cancel(): String {
            return taskShell.commitCmdSync("update_engine_client --cancel")
        }

        suspend fun merge(): String {
            return taskShell.commitCmdSync("update_engine_client --merge")
        }

        suspend fun reset(): String {
            return taskShell.commitCmdSync("update_engine_client --reset_status")
        }

        suspend fun suspend(): String {
            return taskShell.commitCmdSync("update_engine_client --suspend")
        }

        suspend fun resume(): String {
            return taskShell.commitCmdSync("update_engine_client --resume")
        }
    }

    private val reusableShell = ReusableShells.getInstance(
        "update_engine_client",
        redirectErrorStream = true,
    )

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
            if (hasUpdateEngineClient()) {
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

fun String.asMsgText(): String = try {
    logD("原始内容：$this", "update_engine_task")
    this.substring(indexOf("'") + 1, lastIndexOf("'"))
} catch (e: Exception) {
    logE(e.stackTraceToString())
    "unknown error"
}

private fun extractUpdateEngineMessage(log: String): String? {
    val regex = Regex("""'\d+:\s*(.*?)\.'?$""")
    return regex.find(log)?.groupValues?.get(1)?.plus(".")
}