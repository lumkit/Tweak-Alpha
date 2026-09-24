package io.github.lumkit.tweak.common.feature

import android.os.ParcelFileDescriptor
import io.github.lumkit.tweak.adlib.client.FastbootClient
import io.github.lumkit.tweak.adlib.exception.FastbootTransportException
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileBackend
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.joinPath
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.openPrivilegedReadOnlyFd
import io.github.lumkit.tweak.model.RuntimeModeStore
import io.github.lumkit.tweak.model.asNativeFileBackend
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

private const val LINE_FLASH_TAG = "LineFlashRomUtil"

actual object LineFlashRomUtil {

    private const val SPARSE_MAGIC = -316211398 // 0xED26FF3A
    private const val SPARSE_IMAGE_HEADER_SIZE = 28
    private const val SPARSE_CHUNK_HEADER_SIZE = 12
    private const val SPARSE_FILL_DATA_SIZE = 4L
    private const val SPARSE_CHUNK_TYPE_RAW = 0xCAC1
    private const val SPARSE_CHUNK_TYPE_FILL = 0xCAC2
    private const val SPARSE_CHUNK_TYPE_DONT_CARE = 0xCAC3
    private const val SPARSE_CHUNK_TYPE_CRC32 = 0xCAC4

    actual suspend fun inspectRomPackage(rootDir: String): LineFlashRomPackage {
        require(Files.exists(rootDir).getOrNull() == true) { "ROM 目录不存在或不可访问: $rootDir" }
        val imagesDir = rootDir joinPath "images"
        require(Files.exists(imagesDir).getOrNull() == true) { "ROM images 目录不存在: $imagesDir" }

        val antiVersion = readTrimmedText(imagesDir joinPath "anti_version.txt")?.toIntOrNull()
        val securityPatch = readTrimmedText(imagesDir joinPath "platform_security_patch.txt")
        val checksumBundle = LineFlashChecksumBundle(
            crcByPartition = parseChecksumList(imagesDir joinPath "crclist.txt"),
            sparseCrcByPartition = parseChecksumList(imagesDir joinPath "sparsecrclist.txt"),
        )

        val scripts = Files.list(rootDir).getOrNull().orEmpty()
            .filter { path -> path.endsWith(".bat", true) }
            .sortedBy { it.substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT) }
            .map { parseFlashScript(it, antiVersion, securityPatch) }

        require(scripts.isNotEmpty()) { "ROM 目录中未发现 .bat 刷写脚本" }

        return LineFlashRomPackage(
            rootDir = rootDir,
            imagesDir = imagesDir,
            scripts = scripts,
            checksums = checksumBundle,
            packageAntiVersion = antiVersion,
            packageSecurityPatch = securityPatch,
        )
    }

    actual suspend fun validatePackage(
        romPackage: LineFlashRomPackage,
        script: LineFlashScript,
        verifyCrc: Boolean,
        onProgress: ((LineFlashProgress) -> Unit)?,
    ): LineFlashValidationReport {
        val requiredFiles = script.steps.mapNotNullTo(linkedSetOf()) { step ->
            when (step) {
                is LineFlashStep.Flash -> step.fileName
                LineFlashStep.FlashChecksumList -> listOf("crclist.txt", "sparsecrclist.txt").let { null }
                else -> null
            }
        }.toMutableSet()

        val imagesDir = romPackage.imagesDir
        val hasChecksumStep = script.steps.any { it is LineFlashStep.FlashChecksumList }
        val crcListExists = Files.exists(imagesDir joinPath "crclist.txt").getOrNull() == true
        val sparseListExists = Files.exists(imagesDir joinPath "sparsecrclist.txt").getOrNull() == true
        val missingFiles = requiredFiles.filterNot { Files.exists(imagesDir joinPath it).getOrNull() == true }.toMutableList()
        val warnings = mutableListOf<String>()
        val crcResults = mutableListOf<LineFlashFileChecksumResult>()
        val crcByPartition = romPackage.checksums.crcByPartition
        val sparseCrcByPartition = romPackage.checksums.sparseCrcByPartition

        if (hasChecksumStep && !crcListExists && !sparseListExists) {
            missingFiles += "crclist.txt|sparsecrclist.txt"
        }

        if (verifyCrc && (crcByPartition.isNotEmpty() || sparseCrcByPartition.isNotEmpty())) {
            val checksumEntries = script.steps.mapNotNull { step ->
                val flash = step as? LineFlashStep.Flash ?: return@mapNotNull null
                val key = normalizePartitionName(flash.partition)
                // sparsecrclist 使用 sparse chunk CRC；crclist 使用整文件 CRC。二者不可混用。
                sparseCrcByPartition[key]?.let {
                    return@mapNotNull ChecksumWork(flash.partition, flash.fileName, it, sparse = true)
                }
                crcByPartition[key]?.let {
                    return@mapNotNull ChecksumWork(flash.partition, flash.fileName, it, sparse = false)
                }
                null
            }.distinctBy { it.fileName }

            checksumEntries.forEachIndexed { index, work ->
                val filePath = imagesDir joinPath work.fileName
                val actual = if (work.sparse) {
                    computeSparseCrc32(filePath)
                } else {
                    computeCrc32(filePath)
                }
                crcResults += LineFlashFileChecksumResult(work.partition, work.fileName, work.expected, actual)
                onProgress?.invoke(
                    progressOf(
                        stage = LineFlashProgress.Stage.VALIDATING,
                        scriptName = script.name,
                        currentStepIndex = index + 1,
                        totalSteps = checksumEntries.size.coerceAtLeast(1),
                        stepDescription = "校验 ${work.fileName}",
                    )
                )
            }
        } else if (verifyCrc) {
            warnings += "ROM 包未提供可用的 CRC 校验清单"
        }

        return LineFlashValidationReport(
            romPackage = romPackage,
            missingFiles = missingFiles,
            crcResults = crcResults,
            securityWarnings = warnings,
        )
    }

    actual suspend fun flash(
        device: FastbootDevice,
        romPackage: LineFlashRomPackage,
        script: LineFlashScript,
        verifyBeforeFlash: Boolean,
        verifyCrc: Boolean,
        onProgress: ((LineFlashProgress) -> Unit)?,
        onResult: ((LineFlashResult) -> Unit)?,
    ): LineFlashResult {
        val result = runCatching {
            require(FastbootManager.hasPermission(device)) { "Fastboot 设备尚未授权" }

            onProgress?.invoke(
                progressOf(
                    stage = LineFlashProgress.Stage.PREPARING,
                    scriptName = script.name,
                    currentStepIndex = 0,
                    totalSteps = script.steps.size,
                    stepDescription = "准备刷写会话",
                )
            )

            if (verifyBeforeFlash) {
                val validation = validatePackage(
                    romPackage = romPackage,
                    script = script,
                    verifyCrc = verifyCrc,
                    onProgress = onProgress,
                )
                if (!validation.isComplete) {
                    val details = buildList {
                        if (validation.missingFiles.isNotEmpty()) {
                            add("缺少文件: ${validation.missingFiles.joinToString()}")
                        }
                        val failed = validation.crcResults.filterNot { it.matches }
                        if (failed.isNotEmpty()) {
                            add("CRC 校验失败: ${failed.joinToString { it.fileName }}")
                        }
                    }.joinToString("; ")
                    throw IllegalStateException(details.ifBlank { "ROM 包校验失败" })
                }
            }

            executeScript(
                device = device,
                romPackage = romPackage,
                script = script,
                onProgress = onProgress,
            )

            LineFlashResult.Success(
                scriptName = script.name,
                executedSteps = script.steps.size,
                totalSteps = script.steps.size,
            )
        }.getOrElse { throwable ->
            val failure = throwable.toFlashFailure(script)
            logE(throwable.stackTraceToString(), tag = LINE_FLASH_TAG)
            failure
        }

        onResult?.invoke(result)
        return result
    }

    private suspend fun executeScript(
        device: FastbootDevice,
        romPackage: LineFlashRomPackage,
        script: LineFlashScript,
        onProgress: ((LineFlashProgress) -> Unit)?,
    ) {
        val imagesDir = romPackage.imagesDir
        val totalSteps = script.steps.size
        FastbootManager.withSession(device) { client ->
            script.steps.forEachIndexed { index, step ->
                val description = step.describe()
                onProgress?.invoke(
                    progressOf(
                        stage = LineFlashProgress.Stage.FLASHING,
                        scriptName = script.name,
                        currentStepIndex = index + 1,
                        totalSteps = totalSteps,
                        stepDescription = description,
                    )
                )
                try {
                    when (step) {
                        is LineFlashStep.CheckProduct -> {
                            val product = client.getVar("product").trim()
                            require(step.products.any { it.equals(product, true) }) {
                                "ROM 与设备不匹配，设备 product=$product，脚本要求=${step.products.joinToString()}"
                            }
                        }

                        is LineFlashStep.CheckAntiRollback -> {
                            val anti = client.getVar("anti").trim().toIntOrNull() ?: 0
                            require(anti <= step.packageVersion) {
                                "设备 antirollback 版本($anti) 高于 ROM 包(${step.packageVersion})"
                            }
                        }

                        is LineFlashStep.CheckSecurityPatch -> {
                            val current = client.getVar("security-patch-level").trim()
                            if (current.isNotBlank()) {
                                require(compareSecurityPatch(current, step.packageLevel) <= 0) {
                                    "设备安全补丁版本($current) 高于 ROM 包(${step.packageLevel})"
                                }
                            }
                        }

                        LineFlashStep.FlashChecksumList -> {
                            val crcList = imagesDir joinPath "crclist.txt"
                            val sparseList = imagesDir joinPath "sparsecrclist.txt"
                            val crcListExists = Files.exists(crcList).getOrNull() == true
                            val sparseListExists = Files.exists(sparseList).getOrNull() == true
                            if (crcListExists || sparseListExists) {
                                val crcSupported = client.getVar("crc").trim() == "1"
                                require(crcSupported) { "设备不支持 CRC 校验清单刷写" }
                                if (crcListExists) {
                                    flashFile(client, "crclist", crcList)
                                }
                                if (sparseListExists) {
                                    flashFile(client, "sparsecrclist", sparseList)
                                }
                            }
                        }

                        is LineFlashStep.Erase -> client.erase(step.partition)
                        is LineFlashStep.Flash -> {
                            val filePath = imagesDir joinPath step.fileName
                            require(Files.exists(filePath).getOrNull() == true) { "刷写文件不存在: $filePath" }
                            flashFile(client, step.partition, filePath) { sent, total ->
                                onProgress?.invoke(
                                    progressOf(
                                        stage = LineFlashProgress.Stage.FLASHING,
                                        scriptName = script.name,
                                        currentStepIndex = index + 1,
                                        totalSteps = totalSteps,
                                        stepDescription = description,
                                        currentBytes = sent.toLong(),
                                        totalBytes = total.toLong(),
                                    )
                                )
                            }
                        }

                        is LineFlashStep.SetActive -> client.setActiveSlot(step.slot)
                        LineFlashStep.Reboot -> client.reboot()
                        LineFlashStep.OemLock -> client.runSimpleCommand("oem lock")
                        is LineFlashStep.Command -> client.runSimpleCommand(step.command)
                    }
                    logD("Line flash step success: $description", LINE_FLASH_TAG)
                } catch (throwable: Throwable) {
                    throw FlashStepException(index + 1, description, throwable)
                }
            }
        }

        onProgress?.invoke(
            progressOf(
                stage = LineFlashProgress.Stage.FINISHING,
                scriptName = script.name,
                currentStepIndex = totalSteps,
                totalSteps = totalSteps,
                stepDescription = "脚本执行完成",
                currentBytes = 1,
                totalBytes = 1,
            )
        )
    }

    private suspend fun parseFlashScript(
        scriptPath: String,
        antiVersion: Int?,
        securityPatch: String?,
    ): LineFlashScript {
        val lines = readUtf8Text(scriptPath).lines()
        val steps = mutableListOf<LineFlashStep>()
        val products = linkedSetOf<String>()
        // 旧包常把 anti 写在脚本里（set CURRENT_ANTI_VER=1），不一定有 anti_version.txt
        val packageAntiVersion = antiVersion ?: parseScriptAntiVersion(lines)

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("::") || line.startsWith("#")) return@forEach

            parseProducts(line)?.takeIf { it.isNotEmpty() }?.let {
                products += it
                steps += LineFlashStep.CheckProduct(it)
                return@forEach
            }

            // getvar 校验行常包在 for/findstr/grep 管道里，绝不能当成 raw Command 下发
            if (line.contains("getvar anti", ignoreCase = true)) {
                if (packageAntiVersion != null) {
                    steps += LineFlashStep.CheckAntiRollback(packageAntiVersion)
                }
                return@forEach
            }

            if (line.contains("getvar security-patch-level", ignoreCase = true)) {
                if (!securityPatch.isNullOrBlank()) {
                    steps += LineFlashStep.CheckSecurityPatch(securityPatch)
                }
                return@forEach
            }

            if (line.contains("getvar product", ignoreCase = true)) {
                return@forEach
            }

            if (line.contains("flash crclist", ignoreCase = true)) {
                if (steps.none { it is LineFlashStep.FlashChecksumList }) {
                    steps += LineFlashStep.FlashChecksumList
                }
                return@forEach
            }

            parseFastbootAction(line)?.let {
                steps += it
                return@forEach
            }

            if (line.contains("fastboot", ignoreCase = true)) {
                buildFastbootCommand(line)?.let {
                    steps += LineFlashStep.Command(it)
                }
            }
        }

        return LineFlashScript(
            name = scriptPath.fileName(),
            path = scriptPath,
            type = if (scriptPath.endsWith(".sh", true)) LineFlashScriptType.SH else LineFlashScriptType.BAT,
            behavior = scriptPath.fileName().toBehavior(),
            steps = steps.distinctConsecutiveChecks(),
            rawLines = lines,
            expectedProducts = products,
        )
    }

    private fun parseScriptAntiVersion(lines: List<String>): Int? {
        val pattern = Regex("""(?:set\s+)?CURRENT_ANTI_VER\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
        return lines.firstNotNullOfOrNull { line ->
            pattern.find(line.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }

    private fun parseProducts(line: String): Set<String>? {
        if (!line.contains("getvar product", ignoreCase = true)) return null
        val matches = Regex("product:\\s*\\*?([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
            .findAll(line)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toSet()
        return matches.ifEmpty { null }
    }

    private fun parseFastbootAction(line: String): LineFlashStep? {
        val normalized = line.replace("\\", "/")
        val fastbootIndex = normalized.indexOf("fastboot", ignoreCase = true)
        if (fastbootIndex < 0) return null

        val commandLine = normalized.substring(fastbootIndex)
        val tokens = commandLine.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .filterNot { it == "%*" || it == "$@" }
        if (tokens.isEmpty() || !tokens.first().equals("fastboot", true)) return null

        val actionIndex = tokens.indexOfFirst { token ->
            token.equals("erase", true) ||
                token.equals("flash", true) ||
                token.equals("set_active", true) ||
                token.equals("reboot", true) ||
                token.equals("oem", true)
        }
        if (actionIndex < 0) return null

        return when (tokens[actionIndex].lowercase(Locale.ROOT)) {
            "erase" -> {
                val partition = tokens.getOrNull(actionIndex + 1) ?: return null
                LineFlashStep.Erase(partition)
            }

            "flash" -> {
                val partition = tokens.getOrNull(actionIndex + 1) ?: return null
                val pathToken = tokens.getOrNull(actionIndex + 2) ?: return null
                val fileName = pathToken.substringAfterLast('/')
                LineFlashStep.Flash(partition, fileName)
            }

            "set_active" -> {
                val slot = tokens.getOrNull(actionIndex + 1) ?: return null
                LineFlashStep.SetActive(slot)
            }

            "reboot" -> {
                val rebootTarget = tokens.getOrNull(actionIndex + 1)
                if (rebootTarget.equals("bootloader", true)) {
                    LineFlashStep.Command("reboot-bootloader")
                } else {
                    LineFlashStep.Reboot
                }
            }
            "oem" -> {
                val command = tokens.drop(actionIndex).joinToString(" ")
                if (command.equals("oem lock", true)) LineFlashStep.OemLock else LineFlashStep.Command(command)
            }

            else -> null
        }
    }

    private fun buildFastbootCommand(line: String): String? {
        // 含管道/重定向/命令替换的行是 shell 包装，不是可下发的 fastboot 协议命令
        if (lineContainsShellWrapper(line)) return null

        val normalized = line.replace("\\", "/")
        val fastbootIndex = normalized.indexOf("fastboot", ignoreCase = true)
        if (fastbootIndex < 0) return null
        val commandLine = normalized.substring(fastbootIndex)
        val tokens = commandLine.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .filterNot { it == "%*" || it == "$@" }
            .takeWhile { token -> !isShellMetaToken(token) }
        if (tokens.isEmpty() || !tokens.first().equals("fastboot", true)) return null
        val command = tokens.drop(1).joinToString(" ").ifBlank { return null }
        if (command.length > 64) return null
        // getvar 应由类型化步骤处理，避免把校验行误下发
        if (command.startsWith("getvar", ignoreCase = true)) return null
        return command
    }

    private fun lineContainsShellWrapper(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return lower.contains('|') ||
            lower.contains("2>&1") ||
            lower.contains("2^>") ||
            lower.contains("findstr") ||
            lower.contains("grep") ||
            line.contains('`') ||
            lower.contains("for /f") ||
            lower.contains("\$(")
    }

    private fun isShellMetaToken(token: String): Boolean {
        return token.contains('|') ||
            token.contains('>') ||
            token.contains('<') ||
            token == "||" ||
            token == "&&" ||
            token.startsWith('&') ||
            token.contains('^') ||
            token.contains('`') ||
            token.startsWith("$") ||
            token.contains(')') ||
            token.contains('(') ||
            token.contains('\'') ||
            token.contains('"')
    }

    private suspend fun parseChecksumList(path: String): Map<String, Long> {
        val content = readUtf8TextOrNull(path) ?: return emptyMap()
        val isSparseList = path.substringAfterLast('/').substringAfterLast('\\')
            .equals("sparsecrclist.txt", ignoreCase = true)
        return content.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                if (trimmed.equals("CRC-LIST", true) || trimmed.equals("SPARSECRC-LIST", true)) {
                    return@mapNotNull null
                }
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size < 2) return@mapNotNull null
                val partition = normalizePartitionName(parts[0])
                val crc = if (isSparseList) {
                    parseSparseCrcExpected(parts) ?: return@mapNotNull null
                } else {
                    parts[1].removePrefix("0x").removePrefix("0X").toLongOrNull(16)
                        ?: return@mapNotNull null
                }
                partition to crc
            }
            .toMap()
    }

    /**
     * 旧格式: `partition 0xCRC [parts]`；新格式: `partition parts 0xCRC...`。
     * 多段 CRC 依赖设备侧按下载分片校验，本地预校验无法直接对比整图，故跳过。
     */
    private fun parseSparseCrcExpected(parts: List<String>): Long? {
        val second = parts[1]
        val looksLikeHexCrc = second.startsWith("0x", ignoreCase = true) ||
            (second.length >= 7 && second.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        if (looksLikeHexCrc) {
            return second.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
        }
        val partCount = second.toIntOrNull() ?: return null
        if (partCount != 1 || parts.size < 3) return null
        return parts[2].removePrefix("0x").removePrefix("0X").toLongOrNull(16)
    }

    private suspend fun computeCrc32(path: String): Long {
        openReadOnlyStream(path).use { input ->
            val checkedInput = CheckedInputStream(input, CRC32())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (checkedInput.read(buffer) >= 0) {
            }
            return checkedInput.checksum.value
        }
    }

    /**
     * 与小米 gen_sparse_crc 一致：仅对 RAW / FILL 负载做 CRC32。
     */
    private suspend fun computeSparseCrc32(path: String): Long {
        openReadOnlyStream(path).use { raw ->
            val data = DataInputStream(BufferedInputStream(raw, DEFAULT_BUFFER_SIZE))
            val headerBytes = ByteArray(SPARSE_IMAGE_HEADER_SIZE)
            data.readFully(headerBytes)
            val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = header.int
            val major = header.short.toInt() and 0xffff
            val minor = header.short.toInt() and 0xffff
            val fileHeaderSize = header.short.toInt() and 0xffff
            val chunkHeaderSize = header.short.toInt() and 0xffff
            val blockSize = Integer.toUnsignedLong(header.int)
            header.int // total blocks
            val totalChunks = header.int
            require(magic == SPARSE_MAGIC) { "不是有效的 sparse 镜像: $path" }
            require(major == 1 && minor == 0) { "不支持的 sparse 版本 $major.$minor: $path" }
            require(fileHeaderSize == SPARSE_IMAGE_HEADER_SIZE) { "不支持的 sparse 文件头大小: $path" }
            require(chunkHeaderSize == SPARSE_CHUNK_HEADER_SIZE) { "不支持的 sparse chunk 头大小: $path" }
            require(totalChunks >= 0) { "无效的 sparse chunk 数量: $path" }
            if (fileHeaderSize > SPARSE_IMAGE_HEADER_SIZE) {
                skipFully(data, (fileHeaderSize - SPARSE_IMAGE_HEADER_SIZE).toLong())
            }

            val crc = CRC32()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val chunkHeaderBytes = ByteArray(SPARSE_CHUNK_HEADER_SIZE)
            repeat(totalChunks) {
                data.readFully(chunkHeaderBytes)
                val chunk = ByteBuffer.wrap(chunkHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
                val type = chunk.short.toInt() and 0xffff
                chunk.short
                val chunkBlocks = Integer.toUnsignedLong(chunk.int)
                val totalSize = Integer.toUnsignedLong(chunk.int)
                require(totalSize >= chunkHeaderSize.toLong()) { "无效的 sparse chunk 大小: $path" }
                val dataSize = totalSize - chunkHeaderSize
                when (type) {
                    SPARSE_CHUNK_TYPE_RAW -> {
                        var remaining = dataSize
                        while (remaining > 0) {
                            val n = minOf(remaining, buffer.size.toLong()).toInt()
                            data.readFully(buffer, 0, n)
                            crc.update(buffer, 0, n)
                            remaining -= n
                        }
                    }

                    SPARSE_CHUNK_TYPE_FILL -> {
                        require(dataSize == SPARSE_FILL_DATA_SIZE) { "FILL chunk 数据长度必须为 4: $path" }
                        val fill = ByteArray(SPARSE_FILL_DATA_SIZE.toInt())
                        data.readFully(fill)
                        require(blockSize > 0 && blockSize % SPARSE_FILL_DATA_SIZE == 0L) {
                            "无效的 sparse block size: $blockSize"
                        }
                        val fillBuf = ByteArray(blockSize.toInt())
                        var offset = 0
                        while (offset < fillBuf.size) {
                            System.arraycopy(fill, 0, fillBuf, offset, fill.size)
                            offset += fill.size
                        }
                        var block = 0L
                        while (block < chunkBlocks) {
                            crc.update(fillBuf)
                            block++
                        }
                    }

                    SPARSE_CHUNK_TYPE_DONT_CARE -> {
                        require(dataSize == 0L) { "DONT_CARE chunk 不应包含数据: $path" }
                    }

                    SPARSE_CHUNK_TYPE_CRC32 -> skipFully(data, dataSize)

                    else -> error("未知 sparse chunk 类型 0x${type.toString(16)}: $path")
                }
            }
            return crc.value
        }
    }

    private fun skipFully(input: DataInputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val n = minOf(remaining, buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, n)
            if (read < 0) throw EOFException("Unexpected EOF while skipping sparse data")
            remaining -= read
        }
    }

    private data class ChecksumWork(
        val partition: String,
        val fileName: String,
        val expected: Long,
        val sparse: Boolean,
    )

    private fun compareSecurityPatch(current: String, target: String): Int {
        val currentDate = parseSecurityPatchDate(current)
        val targetDate = parseSecurityPatchDate(target)
        if (currentDate == null || targetDate == null) return current.compareTo(target)
        return compareValuesBy(currentDate, targetDate, { it.first }, { it.second }, { it.third })
    }

    private fun parseSecurityPatchDate(value: String): Triple<Int, Int, Int>? {
        val parts = value.trim().split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return Triple(year, month, day)
    }

    private fun normalizePartitionName(partition: String): String = partition
        .removeSuffix("_ab")
        .removeSuffix("_a")
        .removeSuffix("_b")
        .trim()

    private suspend fun readTrimmedText(path: String): String? {
        return readUtf8TextOrNull(path)?.trim()?.ifBlank { null }
    }

    private fun String.fileName(): String = substringAfterLast('/').substringAfterLast('\\')

    private suspend fun readUtf8Text(path: String): String {
        return openReadOnlyStream(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private suspend fun readUtf8TextOrNull(path: String): String? {
        if (Files.exists(path).getOrNull() != true) return null
        return readUtf8Text(path)
    }

    private suspend fun flashFile(
        client: FastbootClient,
        partition: String,
        path: String,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ) {
        openReadOnlyStream(path).use { input ->
            val size = input.channel.size()
            client.flash(partition, input.channel, size, onProgress)
        }
    }

    private suspend fun openReadOnlyStream(path: String): ParcelFileDescriptor.AutoCloseInputStream {
        val backend = resolvePrivilegedBackend(path)
        val pfd = openPrivilegedReadOnlyFd(backend, path)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    private suspend fun resolvePrivilegedBackend(path: String): NativeFileBackend {
        val runtimeMode = RuntimeModeStore.mode.filterNotNull().first()
        val backend = runtimeMode.asNativeFileBackend()
        require(backend != NativeFileBackend.User) {
            "当前运行模式不支持通过特权 Binder 打开文件 FD: $path"
        }
        return backend
    }

    private fun String.toBehavior(): LineFlashBehavior = when {
        contains("except_storage", ignoreCase = true) -> LineFlashBehavior.SAVE_USER_DATA
        contains("lock", ignoreCase = true) -> LineFlashBehavior.CLEAN_ALL_AND_LOCK
        contains("flash_all", ignoreCase = true) -> LineFlashBehavior.CLEAN_ALL
        else -> LineFlashBehavior.CUSTOM
    }

    private fun List<LineFlashStep>.distinctConsecutiveChecks(): List<LineFlashStep> {
        val result = mutableListOf<LineFlashStep>()
        forEach { step ->
            if (result.lastOrNull() == step && step !is LineFlashStep.Flash && step !is LineFlashStep.Erase) {
                return@forEach
            }
            result += step
        }
        return result
    }

    private fun LineFlashStep.describe(): String = when (this) {
        is LineFlashStep.CheckProduct -> "校验机型 ${products.joinToString("/")}"
        is LineFlashStep.CheckAntiRollback -> "校验 AntiRollback <= $packageVersion"
        is LineFlashStep.CheckSecurityPatch -> "校验安全补丁 <= $packageLevel"
        LineFlashStep.FlashChecksumList -> "刷入 CRC 校验清单"
        is LineFlashStep.Erase -> "擦除 $partition"
        is LineFlashStep.Flash -> "刷写 $partition <- $fileName"
        is LineFlashStep.SetActive -> "设置活动槽位 $slot"
        LineFlashStep.Reboot -> "重启设备"
        LineFlashStep.OemLock -> "执行 OEM 锁定"
        is LineFlashStep.Command -> "执行命令 $command"
    }

    private fun progressOf(
        stage: LineFlashProgress.Stage,
        scriptName: String,
        currentStepIndex: Int,
        totalSteps: Int,
        stepDescription: String,
        currentBytes: Long = 0,
        totalBytes: Long = 0,
    ): LineFlashProgress {
        val ratio = when {
            totalBytes > 0 -> currentBytes.toDouble() / totalBytes.toDouble()
            totalSteps > 0 -> currentStepIndex.toDouble() / totalSteps.toDouble()
            else -> 0.0
        }
        return LineFlashProgress(
            stage = stage,
            scriptName = scriptName,
            currentStepIndex = currentStepIndex,
            totalSteps = totalSteps,
            stepDescription = stepDescription,
            currentBytes = currentBytes,
            totalBytes = totalBytes,
            percent = (ratio * 100).toInt().coerceIn(0, 100),
        )
    }

    private fun Throwable.toFlashFailure(script: LineFlashScript): LineFlashResult.Failure {
        val stepException = this as? FlashStepException
        val root = stepException?.cause ?: this
        return LineFlashResult.Failure(
            scriptName = script.name,
            failedStepIndex = stepException?.stepIndex ?: -1,
            failedStep = stepException?.stepDescription ?: "未知步骤",
            message = humanizeFlashError(root),
            cause = root,
        )
    }

    private class FlashStepException(
        val stepIndex: Int,
        val stepDescription: String,
        cause: Throwable,
    ) : IllegalStateException(cause.message ?: stepDescription, cause)
}

/** 将 Fastboot/USB 异常转换为面向用户的说明文案。 */
internal fun humanizeFlashError(throwable: Throwable): String {
    if (throwable.suggestsRebootFastboot()) {
        return USB_SESSION_ERROR_MESSAGE
    }
    return throwable.message.orEmpty().ifBlank { "刷写失败" }
}

internal fun Throwable.suggestsRebootFastboot(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is FastbootTransportException) return true
        val message = current.message.orEmpty()
        if (message.contains("USB bulk", ignoreCase = true) ||
            message.contains("Failed to receive", ignoreCase = true) ||
            message.contains("Failed to send", ignoreCase = true) ||
            message.contains("USB 通信失败")
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

private const val USB_SESSION_ERROR_MESSAGE =
    "USB 通信失败，可能是上次刷写中断导致 Fastboot 会话异常。请重启 Fastboot 设备后重试。"

