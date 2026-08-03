package io.github.lumkit.tweak.common.database.battery

import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.daemon.DaemonPaths
import io.github.lumkit.tweak.common.database.battery.BatteryRecordLogImporter.syncOnStartup
import io.github.lumkit.tweak.common.database.battery.repos.BatteryRecordRepository
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryAppUsageSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSampleEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryRecordSessionEntity
import io.github.lumkit.tweak.common.database.battery.table.BatteryUidPowerEntity
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileResult
import io.github.lumkit.tweak.common.utils.battery.UidPowerMath
import io.github.lumkit.tweak.common.utils.battery.UidPowerReading
import io.github.lumkit.tweak.common.utils.battery.UidpowCodec
import io.github.lumkit.tweak.common.utils.getOrNull
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.server.battery.ApplogWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * 将 TweakServer / 旧 tweakd 电池日志同步到 Room。
 * 支持 v2 `.brlog`（定长）、旁路 `.applog` / `.uidpow` 与旧空格分隔 `.log`。
 */
object BatteryRecordLogImporter {

    private const val TAG = "BatteryRecordLogImporter"
    private const val SAMPLE_BATCH_SIZE = 400
    private const val MAX_BYTES_PER_SYNC = 2L * 1024 * 1024
    private const val MAX_CHUNK_BYTES = 512 * 1024
    private const val BRLOG_HEADER = 64
    private const val BRLOG_RECORD = 32

    private val mutex = Mutex()
    private val repository = BatteryRecordRepository()
    private val json = Json { ignoreUnknownKeys = true }
    private val lastTsCache = HashMap<Long, Long>()
    /** 可在持锁导入期间继续登记，drain 循环会吃掉尾部，避免卡顿丢同步 */
    private val pendingAppendPaths =
        java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val pendingCreatedPaths =
        java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    @Serializable
    private data class ImportState(
        val offsets: Map<String, Long> = emptyMap(),
    )

    private fun stateFile(): File =
        File(application.filesDir, "battery_log_import_state.json")

    private fun loadState(): ImportState {
        val file = stateFile()
        if (!file.isFile) return ImportState()
        return runCatching {
            json.decodeFromString<ImportState>(file.readText())
        }.getOrDefault(ImportState())
    }

    private fun saveState(state: ImportState) {
        stateFile().writeText(json.encodeToString(state))
    }

    suspend fun syncOnStartup() = withContext(Dispatchers.IO) {
        mutex.withLock { importLogsIncremental(budgetLimit = MAX_BYTES_PER_SYNC) }
    }

    /**
     * 页面进场轻量同步：只导入当前活跃会话相关日志，避免全目录重扫堵死实时 append。
     */
    suspend fun syncActiveSessionLogs() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val active = repository.queryActiveSession() ?: return@withLock
            val prefix = "${active.startedAt}_${active.state}."
            val paths = DaemonPaths.resolve()
            ensureLogsDir(paths)
            val targets = listLogFiles(paths)
                .filter { it.substringAfterLast('/').startsWith(prefix) }
            if (targets.isEmpty()) return@withLock
            val mutableOffsets = loadState().offsets.toMutableMap()
            for (path in targets) {
                importPathInto(
                    path = path,
                    mutableOffsets = mutableOffsets,
                    budget = MAX_BYTES_PER_SYNC,
                    forceHeader = false,
                )
            }
            saveState(ImportState(mutableOffsets))
            logD(
                "active sync session=${active.id} files=${targets.size} prefix=$prefix",
                TAG,
            )
        }
    }

    suspend fun syncOnLogAppended(path: String) = withContext(Dispatchers.IO) {
        pendingAppendPaths.add(path)
        mutex.withLock { drainPendingLocked() }
    }

    suspend fun syncOnLogCreated(path: String) = withContext(Dispatchers.IO) {
        pendingCreatedPaths.add(path)
        mutex.withLock { drainPendingLocked() }
    }

    private suspend fun drainPendingLocked() {
        val mutableOffsets = loadState().offsets.toMutableMap()
        while (true) {
            val created = synchronized(pendingCreatedPaths) {
                if (pendingCreatedPaths.isEmpty()) emptyList()
                else pendingCreatedPaths.toList().also { pendingCreatedPaths.clear() }
            }
            val appended = synchronized(pendingAppendPaths) {
                if (pendingAppendPaths.isEmpty()) emptyList()
                else pendingAppendPaths.toList().also { pendingAppendPaths.clear() }
            }
            if (created.isEmpty() && appended.isEmpty()) break

            for (path in created) {
                if (!isLogPath(path)) continue
                val consumed = importPathInto(
                    path = path,
                    mutableOffsets = mutableOffsets,
                    budget = MAX_BYTES_PER_SYNC,
                    forceHeader = true,
                )
                if (consumed.samples > 0 || consumed.bytes > 0L) {
                    logD("created sync path=$path samples=${consumed.samples} bytes=${consumed.bytes}", TAG)
                }
            }
            for (path in appended) {
                if (!isLogPath(path)) continue
                val consumed = importPathInto(
                    path = path,
                    mutableOffsets = mutableOffsets,
                    budget = MAX_BYTES_PER_SYNC,
                    forceHeader = false,
                )
                if (consumed.samples > 0 || consumed.bytes > 0L) {
                    logD("append sync path=$path samples=${consumed.samples} bytes=${consumed.bytes}", TAG)
                }
            }
        }
        saveState(ImportState(mutableOffsets))
    }

    suspend fun syncOnLogRemoved(path: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val state = loadState()
            if (path !in state.offsets) return@withLock
            saveState(ImportState(state.offsets - path))
            logD("removed import offset for $path", TAG)
        }
    }

    /**
     * 删除 `{startedAt}_{state}.brlog` / `.brlog.NNN` / 旧 `.log`，并清 import offset。
     */
    suspend fun deleteLogsForSession(
        startedAt: Long,
        state: Int,
        sessionId: Long? = null,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val paths = DaemonPaths.resolve()
            ensureLogsDir(paths)
            val prefix = "${startedAt}_${state}."
            val targets = LinkedHashSet<String>()
            listLogFiles(paths)
                .filter { path -> path.substringAfterLast('/').startsWith(prefix) }
                .forEach { targets.add(it) }
            loadState().offsets.keys
                .filter { path -> path.substringAfterLast('/').startsWith(prefix) }
                .forEach { targets.add(it) }

            if (targets.isEmpty()) {
                sessionId?.let { lastTsCache.remove(it) }
                logD("no log files for session startedAt=$startedAt state=$state", TAG)
                return@withLock
            }

            val mutableOffsets = loadState().offsets.toMutableMap()
            for (path in targets) {
                deleteLogFile(path)
                mutableOffsets.remove(path)
            }
            saveState(ImportState(mutableOffsets))
            sessionId?.let { lastTsCache.remove(it) }
            logD(
                "deleted ${targets.size} log file(s) for startedAt=$startedAt state=$state",
                TAG,
            )
        }
    }

    private suspend fun deleteLogFile(path: String) {
        val local = File(path)
        if (local.isFile) {
            if (local.delete()) {
                return
            }
        }
        when (val result = Files.delete(path)) {
            is NativeFileResult.Success -> Unit
            is NativeFileResult.Failure -> {
                logE("failed to delete log $path: ${result.error}", tag = TAG)
            }
        }
    }

    /** @deprecated 使用 [syncOnStartup] */
    suspend fun syncAll() = syncOnStartup()

    /** @deprecated 使用 [syncOnStartup] */
    suspend fun syncIncremental() = syncOnStartup()

    private suspend fun importLogsIncremental(budgetLimit: Long) {
        val mark = TimeSource.Monotonic.markNow()
        val paths = DaemonPaths.resolve()
        ensureLogsDir(paths)
        val files = listLogFiles(paths).sortedWith(
            compareBy<String> { isApplogPath(it) || isUidpowPath(it) }.thenBy { it },
        )
        if (files.isEmpty()) {
            logD("no battery log files under ${paths.batteryLogsDir}", TAG)
            return
        }

        val state = loadState()
        val mutableOffsets = state.offsets.toMutableMap()
        var budget = budgetLimit
        var totalImportedSamples = 0
        var filesTouched = 0

        for (path in files) {
            if (budget <= 0L) break
            val consumed = importPathInto(
                path = path,
                mutableOffsets = mutableOffsets,
                budget = budget,
                forceHeader = false,
            )
            if (consumed.bytes > 0L || consumed.samples > 0) {
                filesTouched++
                totalImportedSamples += consumed.samples
            }
            budget -= consumed.bytes
        }

        // uidpow：仅当文件相对上次 offset 有增长（或从未导入）时再解析，避免进页扫全历史
        for (path in files.filter { isUidpowPath(it) }) {
            val fileLen = fileLength(path) ?: continue
            val prev = mutableOffsets[path] ?: -1L
            if (prev >= 0L && fileLen <= prev) continue
            importUidpow(path, fileLen, mutableOffsets)
        }

        saveState(ImportState(mutableOffsets))
        logD(
            "startup sync files=$filesTouched samples=$totalImportedSamples " +
                "elapsedMs=${mark.elapsedNow().inWholeMilliseconds} budgetLeft=$budget",
            TAG,
        )
    }

    private suspend fun importSingleFile(path: String, forceHeader: Boolean) {
        if (!isLogPath(path)) return
        val state = loadState()
        val mutableOffsets = state.offsets.toMutableMap()
        val consumed = importPathInto(
            path = path,
            mutableOffsets = mutableOffsets,
            budget = MAX_BYTES_PER_SYNC,
            forceHeader = forceHeader,
        )
        saveState(ImportState(mutableOffsets))
        if (consumed.samples > 0 || consumed.bytes > 0L) {
            logD("single sync path=$path samples=${consumed.samples} bytes=${consumed.bytes}", TAG)
        }
    }

    private data class Consumed(val bytes: Long, val samples: Int)

    private suspend fun importPathInto(
        path: String,
        mutableOffsets: MutableMap<String, Long>,
        budget: Long,
        forceHeader: Boolean,
    ): Consumed {
        val fileLen = fileLength(path) ?: return Consumed(0, 0)
        var startOffset = (mutableOffsets[path] ?: 0L).coerceAtMost(fileLen)
        if (forceHeader) {
            startOffset = 0L
        }

        if (isUidpowPath(path) || (startOffset == 0L && looksLikeUidpow(path))) {
            return importUidpow(path, fileLen, mutableOffsets)
        }

        if (isApplogPath(path) || (startOffset == 0L && looksLikeApplog(path))) {
            return importApplog(path, fileLen, startOffset, mutableOffsets, budget)
        }

        // brlog：无论有无新字节都先同步 header（endedAt / confirmed / deleted）
        // 否则 session 结束只改头、长度不变时 Room 永远停在「进行中」
        val brlog = isBrlogPath(path) || (startOffset == 0L && looksLikeBrlog(path, 0L))
        if (brlog) {
            refreshBrlogSessionMeta(path)
        }

        if (startOffset >= fileLen) {
            mutableOffsets[path] = fileLen
            return Consumed(0, 0)
        }

        return if (brlog) {
            importBrlog(path, fileLen, startOffset, mutableOffsets, budget)
        } else {
            importLegacyText(path, fileLen, startOffset, mutableOffsets, budget)
        }
    }

    private suspend fun importApplog(
        path: String,
        fileLen: Long,
        startOffset: Long,
        mutableOffsets: MutableMap<String, Long>,
        budget: Long,
    ): Consumed {
        val headerSize = ApplogWriter.HEADER_SIZE.toInt()
        val headerBytes = readBytes(path, 0, headerSize) ?: return Consumed(0, 0)
        if (headerBytes.size < headerSize || !ApplogWriter.isTwA1(headerBytes)) {
            return Consumed(0, 0)
        }
        val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        header.position(4)
        header.short // version
        header.short // headerSize field
        header.int // intervalMs
        val state = header.int
        val startedAt = header.long
        header.long // createdAt
        val endedAt = header.long
        val chargeState = BatteryChargeState.fromCode(state)
        val session = repository.querySessionByStartedAtAndState(startedAt, chargeState)
            ?: return Consumed(0, 0)

        var offset = if (startOffset < headerSize) headerSize.toLong() else startOffset
        if (offset >= fileLen) {
            mutableOffsets[path] = fileLen
            closeOpenUsages(session.id, endedAt)
            return Consumed(0, 0)
        }

        var currentUsage = repository.queryAppUsagesBySessionId(session.id)
            .lastOrNull { it.endedAt == null }
        var samples = 0
        var bytes = 0L

        while (offset < fileLen && bytes < budget) {
            val fixed = readBytes(path, offset, 10) ?: break
            if (fixed.size < 10) break
            val recordBuf = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN)
            val timestamp = recordBuf.long
            val nameLen = recordBuf.short.toInt() and 0xFFFF
            if (nameLen < 0 || nameLen > 512) break
            val nameBytes = readBytes(path, offset + 10, nameLen) ?: break
            if (nameBytes.size < nameLen) break
            val pkg = nameBytes.decodeToString()
            val recordSize = 10L + nameLen

            if (pkg.isBlank()) {
                offset += recordSize
                bytes += recordSize
                continue
            }
            val sampleId = repository.querySampleIdBySessionAndTimestamp(session.id, timestamp)
            if (sampleId == null) {
                break
            }

            if (currentUsage == null || currentUsage.packageName != pkg) {
                if (currentUsage != null) {
                    repository.updateAppUsage(currentUsage.copy(endedAt = timestamp))
                }
                val usageId = repository.insertAppUsage(
                    BatteryAppUsageEntity(
                        sessionId = session.id,
                        packageName = pkg,
                        startedAt = timestamp,
                        endedAt = null,
                    ),
                )
                currentUsage = BatteryAppUsageEntity(
                    id = usageId,
                    sessionId = session.id,
                    packageName = pkg,
                    startedAt = timestamp,
                    endedAt = null,
                )
            }
            repository.insertAppUsageSamples(
                listOf(BatteryAppUsageSampleEntity(usageId = currentUsage.id, sampleId = sampleId)),
            )
            offset += recordSize
            bytes += recordSize
            samples++
        }

        if (endedAt > 0L) {
            closeOpenUsages(session.id, endedAt)
        }
        mutableOffsets[path] = offset
        return Consumed(bytes, samples)
    }

    private suspend fun closeOpenUsages(sessionId: Long, endedAt: Long) {
        if (endedAt <= 0L) return
        repository.queryAppUsagesBySessionId(sessionId)
            .filter { it.endedAt == null }
            .forEach { usage ->
                repository.updateAppUsage(usage.copy(endedAt = endedAt))
            }
    }

    /** 仅根据文件头 upsert/结束对应 Room session，不消费样本字节 */
    private suspend fun refreshBrlogSessionMeta(path: String): Long? {
        val headerBytes = readBytes(path, 0, BRLOG_HEADER) ?: return null
        if (headerBytes.size < BRLOG_HEADER || !isTwb2(headerBytes)) return null
        return applyBrlogHeader(headerBytes)
    }

    private suspend fun importBrlog(
        path: String,
        fileLen: Long,
        startOffset: Long,
        mutableOffsets: MutableMap<String, Long>,
        budget: Long,
    ): Consumed {
        var offset = startOffset
        var samples = 0
        var bytes = 0L

        // 样本必须绑定本文件 header 对应的 session（startedAt + state）
        val sessionId = refreshBrlogSessionMeta(path)
            ?: return Consumed(0, 0)

        if (offset < BRLOG_HEADER) {
            offset = BRLOG_HEADER.toLong()
            mutableOffsets[path] = offset
        }

        // 对齐到记录边界
        if (offset > BRLOG_HEADER) {
            val body = offset - BRLOG_HEADER
            val mis = body % BRLOG_RECORD
            if (mis != 0L) {
                offset -= mis
            }
        }

        val pending = ArrayList<BatteryRecordSampleEntity>(SAMPLE_BATCH_SIZE)
        while (offset + BRLOG_RECORD <= fileLen && bytes < budget) {
            val want = minOf(
                ((fileLen - offset) / BRLOG_RECORD).toInt() * BRLOG_RECORD,
                MAX_CHUNK_BYTES / BRLOG_RECORD * BRLOG_RECORD,
                ((budget - bytes) / BRLOG_RECORD).toInt().coerceAtLeast(0) * BRLOG_RECORD,
            )
            if (want < BRLOG_RECORD) break
            val chunk = readBytes(path, offset, want) ?: break
            if (chunk.size < BRLOG_RECORD) break
            val complete = chunk.size - (chunk.size % BRLOG_RECORD)
            var i = 0
            while (i + BRLOG_RECORD <= complete) {
                val sample = parseBrlogRecord(chunk, i, sessionId)
                if (sample != null) {
                    val lastTs = lastTsCache[sessionId]
                        ?: (repository.maxSampleTimestamp(sessionId) ?: 0L).also {
                            lastTsCache[sessionId] = it
                        }
                    if (sample.timestamp > lastTs) {
                        pending.add(sample)
                        lastTsCache[sessionId] = sample.timestamp
                        samples++
                        if (pending.size >= SAMPLE_BATCH_SIZE) {
                            flushSamples(pending)
                        }
                    }
                }
                i += BRLOG_RECORD
            }
            offset += complete
            bytes += complete
            if (complete == 0) break
        }
        flushSamples(pending)
        // 尾部再刷一次头（closeSession 可能刚好写完 endedAt）
        refreshBrlogSessionMeta(path)
        mutableOffsets[path] = offset
        return Consumed(bytes, samples)
    }

    private suspend fun importLegacyText(
        path: String,
        fileLen: Long,
        startOffset: Long,
        mutableOffsets: MutableMap<String, Long>,
        budget: Long,
    ): Consumed {
        var offset = startOffset
        var sessionId: Long? = null
        var samples = 0
        var bytes = 0L
        val pendingSamples = ArrayList<BatteryRecordSampleEntity>(SAMPLE_BATCH_SIZE)

        while (offset < fileLen && bytes < budget) {
            val want = minOf(MAX_CHUNK_BYTES.toLong(), fileLen - offset, budget - bytes).toInt()
            if (want <= 0) break
            val chunk = readFromOffsetAsString(path, offset, want) ?: break
            if (chunk.isEmpty()) break
            val (consumed, nextSessionId, sampleCount) = parseAndApplyChunk(
                text = chunk,
                sessionId = sessionId,
                pendingSamples = pendingSamples,
            )
            if (consumed <= 0) {
                if (offset + chunk.length >= fileLen) offset = fileLen
                break
            }
            sessionId = nextSessionId
            offset += consumed
            bytes += consumed
            samples += sampleCount
        }
        flushSamples(pendingSamples)
        mutableOffsets[path] = offset
        return Consumed(bytes, samples)
    }

    private suspend fun ensureLogsDir(paths: DaemonPaths.Resolved) {
        runCatching { File(paths.batteryLogsDir).mkdirs() }
        if (!File(paths.batteryLogsDir).isDirectory) {
            Files.mkdirs(paths.batteryLogsDir)
        }
    }

    private suspend fun listLogFiles(paths: DaemonPaths.Resolved): List<String> {
        val local = File(paths.batteryLogsDir)
        if (local.isDirectory) {
            val listed = local.listFiles()
                ?.filter { it.isFile && isLogPath(it.name) }
                ?.map { it.absolutePath }
            if (!listed.isNullOrEmpty()) return listed
        }
        return when (val listed = Files.list(paths.batteryLogsDir)) {
            is NativeFileResult.Success -> listed.value
                .filter { isLogPath(it) }
                .map { name -> if (name.startsWith("/")) name else "${paths.batteryLogsDir}/$name" }
            is NativeFileResult.Failure -> emptyList()
        }
    }

    private fun isLogPath(nameOrPath: String): Boolean {
        val name = nameOrPath.substringAfterLast('/')
        return name.contains(".brlog") ||
            name.contains(".applog") ||
            name.contains(".uidpow") ||
            name.endsWith(".log") ||
            name.contains(".log.")
    }

    private fun isBrlogPath(path: String): Boolean =
        path.substringAfterLast('/').contains(".brlog")

    private fun isApplogPath(path: String): Boolean =
        path.substringAfterLast('/').contains(".applog")

    private fun isUidpowPath(path: String): Boolean =
        path.substringAfterLast('/').contains(".uidpow")

    private suspend fun looksLikeBrlog(path: String, offset: Long): Boolean {
        if (offset > 0) return false
        val head = readBytes(path, 0, 4) ?: return false
        return isTwb2(head)
    }

    private suspend fun looksLikeApplog(path: String): Boolean {
        val head = readBytes(path, 0, 4) ?: return false
        return ApplogWriter.isTwA1(head)
    }

    private suspend fun looksLikeUidpow(path: String): Boolean {
        val head = readFromOffsetAsString(path, 0, 64) ?: return false
        val firstLine = head.lineSequence().firstOrNull()?.trim().orEmpty()
        return UidpowCodec.decodeHeader(firstLine) != null
    }

    private suspend fun importUidpow(
        path: String,
        fileLen: Long,
        mutableOffsets: MutableMap<String, Long>,
    ): Consumed {
        val text = readFromOffsetAsString(path, 0, fileLen.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            ?: return Consumed(0, 0)
        val parsed = UidpowCodec.parseFile(text)
        if (parsed == null) {
            mutableOffsets[path] = fileLen
            return Consumed(fileLen, 0)
        }
        val (header, frames) = parsed
        val session = repository.querySessionByStartedAtAndState(
            startedAt = header.startedAt,
            state = BatteryChargeState.fromCode(header.state),
        )
        if (session == null) {
            logD("uidpow session missing startedAt=${header.startedAt} state=${header.state}", TAG)
            // session 可能尚未由 brlog 导入；下次再试
            return Consumed(0, 0)
        }
        if (frames.size < 2) {
            mutableOffsets[path] = fileLen
            return Consumed(fileLen, 0)
        }
        val first = frames.first()
        val last = frames.last()
        val startMap = first.entries.associate { e ->
            e.uid to UidPowerReading(
                uid = e.uid,
                totalMah = e.totalMah,
                fgMah = e.fgMah,
                bgMah = e.bgMah,
                fgsMah = e.fgsMah,
                cachedMah = e.cachedMah,
            )
        }
        val endMap = last.entries.associate { e ->
            e.uid to UidPowerReading(
                uid = e.uid,
                totalMah = e.totalMah,
                fgMah = e.fgMah,
                bgMah = e.bgMah,
                fgsMah = e.fgsMah,
                cachedMah = e.cachedMah,
            )
        }
        val deltas = UidPowerMath.diff(startMap, endMap)
        val now = Clock.System.now().toEpochMilliseconds()
        val pm = application.packageManager
        val rows = deltas.map { d ->
            val pkg = runCatching {
                pm.getPackagesForUid(d.uid)?.firstOrNull()
            }.getOrNull()
            BatteryUidPowerEntity(
                sessionId = session.id,
                uid = d.uid,
                packageName = pkg,
                deltaMah = d.deltaMah,
                fgMah = d.fgMah,
                bgMah = d.bgMah,
                fgsMah = d.fgsMah,
                capturedAt = last.capturedAt,
                updatedAt = now,
            )
        }
        repository.replaceUidPowers(session.id, rows)
        logD("uidpow imported session=${session.id} uids=${rows.size}", TAG)
        mutableOffsets[path] = fileLen
        return Consumed(fileLen, 0)
    }

    private fun isTwb2(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'T'.code.toByte() &&
            bytes[1] == 'W'.code.toByte() &&
            bytes[2] == 'B'.code.toByte() &&
            bytes[3] == '2'.code.toByte()

    private suspend fun fileLength(path: String): Long? {
        val local = File(path)
        if (local.isFile) return local.length()
        return Files.length(path).getOrNull()
    }

    private suspend fun readBytes(path: String, offset: Long, maxBytes: Int): ByteArray? {
        return try {
            val local = File(path)
            if (local.isFile && local.canRead()) {
                return RandomAccessFile(local, "r").use { raf ->
                    if (offset >= raf.length()) return@use ByteArray(0)
                    raf.seek(offset)
                    val remain = (raf.length() - offset).toInt().coerceAtMost(maxBytes)
                    val buf = ByteArray(remain)
                    val read = raf.read(buf)
                    if (read <= 0) ByteArray(0) else if (read == buf.size) buf else buf.copyOf(read)
                }
            }
            val count = maxBytes.coerceAtLeast(1)
            val out = ReusableShells.execSync(
                "dd if=${shellQuote(path)} bs=1 skip=$offset count=$count 2>/dev/null",
            )
            out.toByteArray(Charsets.ISO_8859_1)
        } catch (e: Exception) {
            logE("readBytes $path@$offset failed: ${e.message}", e, TAG)
            null
        }
    }

    private suspend fun readFromOffsetAsString(path: String, offset: Long, maxBytes: Int): String? {
        val bytes = readBytes(path, offset, maxBytes) ?: return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun shellQuote(path: String): String =
        "'" + path.replace("'", "'\\''") + "'"

    private suspend fun applyBrlogHeader(header: ByteArray): Long? {
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val version = buf.short.toInt() and 0xFFFF
        if (version != 2) return null
        buf.short // headerSize
        buf.short // recordSize
        buf.short // flags
        val intervalMs = buf.int
        val state = buf.int
        val confirmMs = buf.int
        val startedAt = buf.long
        val createdAt = buf.long
        val confirmed = buf.get().toInt() == 1
        val deleted = buf.get().toInt() == 1
        buf.short // pad
        val endedAtRaw = buf.long
        // -1 表示进行中；正数结束时间
        val endedAt = endedAtRaw.takeIf { it > 0L }

        val chargeState = BatteryChargeState.fromCode(state)
        val existing = repository.querySessionByStartedAtAndState(
            startedAt = startedAt,
            state = chargeState,
        )
        return if (existing != null) {
            if (deleted && !existing.confirmed) {
                repository.softDeleteUnconfirmedSession(
                    existing.id,
                    endedAt = endedAt ?: System.currentTimeMillis(),
                )
            } else {
                repository.updateSession(
                    existing.copy(
                        intervalMs = intervalMs,
                        confirmMs = confirmMs,
                        confirmed = confirmed || existing.confirmed,
                        deleted = deleted || existing.deleted,
                        // 一旦文件头写了结束时间，必须落到 Room，避免充电页仍当活跃 session
                        endedAt = endedAt ?: existing.endedAt,
                    ),
                )
            }
            if (!lastTsCache.containsKey(existing.id)) {
                lastTsCache[existing.id] = repository.maxSampleTimestamp(existing.id) ?: 0L
            }
            existing.id
        } else {
            val id = repository.insertSession(
                BatteryRecordSessionEntity(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    intervalMs = intervalMs,
                    state = state,
                    confirmed = confirmed,
                    confirmMs = confirmMs,
                    deleted = deleted,
                    createdAt = createdAt,
                ),
            )
            lastTsCache[id] = 0L
            id
        }
    }

    private fun parseBrlogRecord(chunk: ByteArray, at: Int, sessionId: Long): BatteryRecordSampleEntity? {
        if (at + BRLOG_RECORD > chunk.size) return null
        val buf = ByteBuffer.wrap(chunk, at, BRLOG_RECORD).order(ByteOrder.LITTLE_ENDIAN)
        val timestamp = buf.long
        val level = buf.int
        val voltageMv = buf.int
        val currentMa = buf.int
        val tempCenti = buf.short
        val screenOn = buf.get().toInt() == 1
        if (level !in 0..100) return null
        return BatteryRecordSampleEntity(
            sessionId = sessionId,
            timestamp = timestamp,
            level = level,
            voltageMv = voltageMv.takeUnless { it == Int.MIN_VALUE },
            temperatureC = tempCenti.takeUnless { it == Short.MIN_VALUE }?.let { it / 100f },
            screenOn = screenOn,
            currentMa = currentMa.takeUnless { it == Int.MIN_VALUE },
        )
    }

    private suspend fun parseAndApplyChunk(
        text: String,
        sessionId: Long?,
        pendingSamples: MutableList<BatteryRecordSampleEntity>,
    ): Triple<Int, Long?, Int> {
        var sid = sessionId
        var sampleCount = 0
        var lastNewline = -1
        var lineStart = 0
        val n = text.length
        var i = 0
        while (i < n) {
            if (text[i] != '\n') {
                i++
                continue
            }
            lastNewline = i
            val rawLine = text.substring(lineStart, i).trimEnd('\r')
            lineStart = i + 1
            i++
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            when {
                trimmed.startsWith("M ") -> {
                    flushSamples(pendingSamples)
                    sid = applyMetaLine(trimmed) ?: sid
                }
                trimmed.startsWith("S ") -> {
                    val currentSid = sid ?: continue
                    val sample = parseSampleLine(trimmed, currentSid) ?: continue
                    val lastTs = lastTsCache[currentSid]
                        ?: (repository.maxSampleTimestamp(currentSid) ?: 0L).also {
                            lastTsCache[currentSid] = it
                        }
                    if (sample.timestamp <= lastTs) continue
                    pendingSamples.add(sample)
                    lastTsCache[currentSid] = sample.timestamp
                    sampleCount++
                    if (pendingSamples.size >= SAMPLE_BATCH_SIZE) {
                        flushSamples(pendingSamples)
                    }
                }
                trimmed.startsWith("E ") -> {
                    flushSamples(pendingSamples)
                    val currentSid = sid ?: continue
                    applyEndLine(trimmed, currentSid)
                }
            }
        }
        val consumed = if (lastNewline >= 0) lastNewline + 1 else 0
        return Triple(consumed, sid, sampleCount)
    }

    private suspend fun flushSamples(pending: MutableList<BatteryRecordSampleEntity>) {
        if (pending.isEmpty()) return
        repository.insertSamples(pending.toList())
        pending.clear()
    }

    private suspend fun applyMetaLine(trimmed: String): Long? {
        val parts = trimmed.split(' ')
        if (parts.size < 9) return null
        val startedAt = parts[1].toLongOrNull() ?: return null
        val intervalMs = parts[2].toIntOrNull() ?: return null
        val state = parts[3].toIntOrNull() ?: return null
        val confirmMs = parts[4].toIntOrNull() ?: BatteryRecordDefaults.CONFIRM_MS
        val createdAt = parts[5].toLongOrNull() ?: startedAt
        val confirmed = parts[6] == "1"
        val deleted = parts[7] == "1"
        val endedAt = parts[8].toLongOrNull()?.takeIf { it > 0 }

        val existing = repository.querySessionByStartedAtAndState(
            startedAt = startedAt,
            state = BatteryChargeState.fromCode(state),
        )
        return if (existing != null) {
            repository.updateSession(
                existing.copy(
                    intervalMs = intervalMs,
                    confirmMs = confirmMs,
                    confirmed = confirmed || existing.confirmed,
                    deleted = deleted || existing.deleted,
                    endedAt = endedAt ?: existing.endedAt,
                ),
            )
            if (!lastTsCache.containsKey(existing.id)) {
                lastTsCache[existing.id] = repository.maxSampleTimestamp(existing.id) ?: 0L
            }
            existing.id
        } else {
            val id = repository.insertSession(
                BatteryRecordSessionEntity(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    intervalMs = intervalMs,
                    state = state,
                    confirmed = confirmed,
                    confirmMs = confirmMs,
                    deleted = deleted,
                    createdAt = createdAt,
                ),
            )
            lastTsCache[id] = 0L
            id
        }
    }

    private fun parseSampleLine(trimmed: String, sessionId: Long): BatteryRecordSampleEntity? {
        val parts = trimmed.split(' ')
        if (parts.size < 7) return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        val level = parts[2].toIntOrNull() ?: return null
        return BatteryRecordSampleEntity(
            sessionId = sessionId,
            timestamp = timestamp,
            level = level,
            voltageMv = parts[3].takeIf { it != "-" }?.toIntOrNull(),
            temperatureC = parts[4].takeIf { it != "-" }?.toFloatOrNull(),
            screenOn = parts[5] == "1",
            currentMa = parts[6].takeIf { it != "-" }?.toIntOrNull(),
        )
    }

    private suspend fun applyEndLine(trimmed: String, sessionId: Long) {
        val parts = trimmed.split(' ')
        if (parts.size < 4) return
        val endedAt = parts[1].toLongOrNull() ?: return
        val confirmed = parts[2] == "1"
        val deleted = parts[3] == "1"
        val target = repository.querySessionById(sessionId) ?: return
        if (deleted && !target.confirmed) {
            repository.softDeleteUnconfirmedSession(target.id, endedAt)
        } else {
            repository.updateSession(
                target.copy(
                    endedAt = endedAt,
                    confirmed = confirmed || target.confirmed,
                    deleted = deleted || target.deleted,
                ),
            )
        }
    }
}
