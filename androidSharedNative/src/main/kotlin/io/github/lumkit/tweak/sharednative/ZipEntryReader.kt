package io.github.lumkit.tweak.sharednative

import android.os.Bundle
import java.io.RandomAccessFile
import java.util.zip.ZipException

/**
 * ZIP文件条目读取器，用于解析ZIP文件的中央目录。
 * 实现符合 PKZIP APPNOTE.TXT 规范，支持ZIP64格式（大文件）。
 */
internal object ZipEntryReader {

    private const val CENTRAL_DIR_SIGNATURE = 0x02014b50
    private const val END_OF_CENTRAL_DIR_SIGNATURE = 0x06054b50
    private const val ZIP64_END_OF_CENTRAL_DIR_SIGNATURE = 0x06064b50L
    private const val ZIP64_END_OF_CENTRAL_DIR_LOCATOR_SIGNATURE = 0x07064b50
    private const val ZIP64_EXTRA_FIELD_TAG = 0x0001

    /**
     * 读取ZIP文件的所有条目信息。
     *
     * @param path ZIP文件路径
     * @return 包含所有条目信息的Bundle列表
     * @throws ZipException 如果ZIP文件格式不正确
     */
    fun readEntries(path: String): List<Bundle> {
        RandomAccessFile(path, "r").use { raf ->
            // 查找并解析End of Central Directory记录
            val eocd = findAndParseEOCD(raf)
            
            // 定位到Central Directory开始位置
            raf.seek(eocd.centralDirOffset)
            
            val entries = mutableListOf<Bundle>()
            
            // 读取所有Central Directory条目
            for (i in 0 until eocd.totalEntries) {
                val entry = readCentralDirEntry(raf)
                entries.add(entry)
            }
            
            return entries
        }
    }

    /**
     * 查找并解析End of Central Directory记录。
     */
    private fun findAndParseEOCD(raf: RandomAccessFile): EOCDRecord {
        val fileSize = raf.length()
        
        // EOCD最小22字节，最大为22+65535字节（包含注释）
        val maxSearchSize = minOf(fileSize, 65557L)
        val searchStart = maxOf(0L, fileSize - maxSearchSize)
        
        raf.seek(searchStart)
        val buffer = ByteArray(maxSearchSize.toInt())
        raf.readFully(buffer)
        
        // 从后向前搜索EOCD签名
        var eocdOffset = -1
        for (i in buffer.size - 22 downTo 0) {
            if (buffer.readIntLE(i) == END_OF_CENTRAL_DIR_SIGNATURE) {
                eocdOffset = i
                break
            }
        }
        
        if (eocdOffset == -1) {
            throw ZipException("Invalid ZIP central directory header")
        }
        
        val eocd = parseEOCD(buffer, eocdOffset)
        
        // 检查是否为ZIP64格式
        if (eocd.totalEntries == 0xFFFF || eocd.centralDirOffset == 0xFFFFFFFFL) {
            return parseZip64EOCD(raf, searchStart + eocdOffset)
        }
        
        return eocd
    }

    /**
     * 解析标准的End of Central Directory记录。
     */
    private fun parseEOCD(buffer: ByteArray, offset: Int): EOCDRecord {
        var pos = offset + 4 // 跳过签名
        
        val diskNumber = buffer.readShortLE(pos)
        pos += 2
        val centralDirDisk = buffer.readShortLE(pos)
        pos += 2
        val diskEntries = buffer.readShortLE(pos)
        pos += 2
        val totalEntries = buffer.readShortLE(pos)
        pos += 2
        val centralDirSize = buffer.readIntLE(pos)
        pos += 4
        val centralDirOffset = buffer.readIntLE(pos)
        pos += 4
        val commentLength = buffer.readShortLE(pos)
        
        return EOCDRecord(
            diskNumber = diskNumber,
            centralDirDisk = centralDirDisk,
            diskEntries = diskEntries,
            totalEntries = totalEntries,
            centralDirSize = centralDirSize.toLong() and 0xFFFFFFFFL,
            centralDirOffset = centralDirOffset.toLong() and 0xFFFFFFFFL,
            commentLength = commentLength
        )
    }

    /**
     * 解析ZIP64格式的End of Central Directory记录。
     */
    private fun parseZip64EOCD(raf: RandomAccessFile, eocdOffset: Long): EOCDRecord {
        // ZIP64 EOCD Locator在标准EOCD之前20字节
        val locatorOffset = eocdOffset - 20
        if (locatorOffset < 0) {
            throw ZipException("Invalid ZIP64 format")
        }
        
        raf.seek(locatorOffset)
        val locatorBuffer = ByteArray(20)
        raf.readFully(locatorBuffer)
        
        // 验证ZIP64 EOCD Locator签名
        if (locatorBuffer.readIntLE(0) != ZIP64_END_OF_CENTRAL_DIR_LOCATOR_SIGNATURE) {
            throw ZipException("Invalid ZIP64 End of Central Directory Locator")
        }
        
        // 读取ZIP64 EOCD的偏移量
        val zip64EOCDOffset = locatorBuffer.readLongLE(8)
        
        // 读取ZIP64 EOCD
        raf.seek(zip64EOCDOffset)
        val zip64Buffer = ByteArray(56) // ZIP64 EOCD最小56字节
        raf.readFully(zip64Buffer)
        
        // 验证ZIP64 EOCD签名（4字节）
        if ((zip64Buffer.readIntLE(0).toLong() and 0xFFFFFFFFL) != ZIP64_END_OF_CENTRAL_DIR_SIGNATURE) {
            throw ZipException("Invalid ZIP64 End of Central Directory")
        }
        
        var pos = 4 // 跳过签名（4字节）
        val eocdSize = zip64Buffer.readLongLE(pos)
        pos += 8
        val versionMadeBy = zip64Buffer.readShortLE(pos)
        pos += 2
        val versionNeeded = zip64Buffer.readShortLE(pos)
        pos += 2
        val diskNumber = zip64Buffer.readIntLE(pos)
        pos += 4
        val centralDirDisk = zip64Buffer.readIntLE(pos)
        pos += 4
        val diskEntries = zip64Buffer.readLongLE(pos)
        pos += 8
        val totalEntries = zip64Buffer.readLongLE(pos)
        pos += 8
        val centralDirSize = zip64Buffer.readLongLE(pos)
        pos += 8
        val centralDirOffset = zip64Buffer.readLongLE(pos)
        
        return EOCDRecord(
            diskNumber = diskNumber,
            centralDirDisk = centralDirDisk,
            diskEntries = diskEntries.toInt(),
            totalEntries = totalEntries.toInt(),
            centralDirSize = centralDirSize,
            centralDirOffset = centralDirOffset,
            commentLength = 0
        )
    }

    /**
     * 读取单个Central Directory条目。
     */
    private fun readCentralDirEntry(raf: RandomAccessFile): Bundle {
        val headerBuffer = ByteArray(46) // Central Directory Header固定部分长度
        raf.readFully(headerBuffer)
        
        var pos = 0
        val signature = headerBuffer.readIntLE(pos)
        if (signature != CENTRAL_DIR_SIGNATURE) {
            throw ZipException("Invalid central directory entry signature: 0x${signature.toString(16)}")
        }
        pos += 4
        
        val versionMadeBy = headerBuffer.readShortLE(pos)
        pos += 2
        val versionNeeded = headerBuffer.readShortLE(pos)
        pos += 2
        val flags = headerBuffer.readShortLE(pos)
        pos += 2
        val compressionMethod = headerBuffer.readShortLE(pos)
        pos += 2
        val modTime = headerBuffer.readShortLE(pos)
        pos += 2
        val modDate = headerBuffer.readShortLE(pos)
        pos += 2
        val crc32 = headerBuffer.readIntLE(pos)
        pos += 4
        var compressedSize = headerBuffer.readIntLE(pos).toLong() and 0xFFFFFFFFL
        pos += 4
        var uncompressedSize = headerBuffer.readIntLE(pos).toLong() and 0xFFFFFFFFL
        pos += 4
        val nameLength = headerBuffer.readShortLE(pos)
        pos += 2
        val extraLength = headerBuffer.readShortLE(pos)
        pos += 2
        val commentLength = headerBuffer.readShortLE(pos)
        pos += 2
        val diskNumberStart = headerBuffer.readShortLE(pos)
        pos += 2
        val internalAttrs = headerBuffer.readShortLE(pos)
        pos += 2
        val externalAttrs = headerBuffer.readIntLE(pos)
        pos += 4
        var localHeaderOffset = headerBuffer.readIntLE(pos).toLong() and 0xFFFFFFFFL
        
        // 读取文件名
        val nameBytes = ByteArray(nameLength)
        raf.readFully(nameBytes)
        val name = String(nameBytes, Charsets.UTF_8)
        
        // 解析extra field（可能包含ZIP64扩展信息）
        if (extraLength > 0) {
            val extraBytes = ByteArray(extraLength)
            raf.readFully(extraBytes)
            
            // 检查是否需要ZIP64扩展信息
            if (uncompressedSize == 0xFFFFFFFFL || compressedSize == 0xFFFFFFFFL || localHeaderOffset == 0xFFFFFFFFL) {
                val zip64Extra = parseZip64ExtraField(extraBytes)
                if (zip64Extra != null) {
                    if (uncompressedSize == 0xFFFFFFFFL && zip64Extra.uncompressedSize != -1L) {
                        uncompressedSize = zip64Extra.uncompressedSize
                    }
                    if (compressedSize == 0xFFFFFFFFL && zip64Extra.compressedSize != -1L) {
                        compressedSize = zip64Extra.compressedSize
                    }
                    if (localHeaderOffset == 0xFFFFFFFFL && zip64Extra.localHeaderOffset != -1L) {
                        localHeaderOffset = zip64Extra.localHeaderOffset
                    }
                }
            }
        }
        
        // 跳过注释
        if (commentLength > 0) {
            raf.skipBytes(commentLength)
        }
        
        // 判断是否为目录
        val isDirectory = name.endsWith("/") || (externalAttrs and 0x10) != 0
        
        // 转换DOS时间为Unix时间戳（毫秒）
        val time = dosTimeToUnixTime(modDate, modTime)
        
        return Bundle().apply {
            putString(NativeFileBundles.KEY_NAME, name)
            putBoolean(NativeFileBundles.KEY_IS_DIRECTORY, isDirectory)
            putLong(NativeFileBundles.KEY_SIZE, uncompressedSize)
            putLong(NativeFileBundles.KEY_COMPRESSED_SIZE, compressedSize)
            putLong(NativeFileBundles.KEY_CRC, crc32.toLong() and 0xFFFFFFFFL)
            putLong(NativeFileBundles.KEY_TIME, time)
            putLong(NativeFileBundles.KEY_OFFSET, localHeaderOffset)
        }
    }

    /**
     * 解析ZIP64 Extra Field。
     */
    private fun parseZip64ExtraField(extraBytes: ByteArray): Zip64ExtraField? {
        var pos = 0
        while (pos + 4 <= extraBytes.size) {
            val tag = extraBytes.readShortLE(pos)
            pos += 2
            val size = extraBytes.readShortLE(pos)
            pos += 2
            
            if (tag == ZIP64_EXTRA_FIELD_TAG && pos + size <= extraBytes.size) {
                var fieldPos = pos
                var uncompressedSize = -1L
                var compressedSize = -1L
                var localHeaderOffset = -1L
                
                // ZIP64 Extra Field格式：
                // Original Size (8 bytes)
                // Compressed Size (8 bytes)
                // Relative Header Offset (8 bytes)
                // Disk Start Number (4 bytes)
                
                if (fieldPos + 8 <= pos + size) {
                    uncompressedSize = extraBytes.readLongLE(fieldPos)
                    fieldPos += 8
                }
                if (fieldPos + 8 <= pos + size) {
                    compressedSize = extraBytes.readLongLE(fieldPos)
                    fieldPos += 8
                }
                if (fieldPos + 8 <= pos + size) {
                    localHeaderOffset = extraBytes.readLongLE(fieldPos)
                    fieldPos += 8
                }
                
                return Zip64ExtraField(uncompressedSize, compressedSize, localHeaderOffset)
            }
            
            pos += size
        }
        
        return null
    }

    /**
     * 将DOS时间转换为Unix时间戳（毫秒）。
     */
    private fun dosTimeToUnixTime(date: Int, time: Int): Long {
        val year = 1980 + ((date shr 9) and 0x7F)
        val month = (date shr 5) and 0x0F
        val day = date and 0x1F
        val hour = (time shr 11) and 0x1F
        val minute = (time shr 5) and 0x3F
        val second = (time and 0x1F) * 2
        
        return try {
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            calendar.set(year, month - 1, day, hour, minute, second)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 从字节数组读取小端序Short。
     */
    private fun ByteArray.readShortLE(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8))
    }

    /**
     * 从字节数组读取小端序Int。
     */
    private fun ByteArray.readIntLE(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                ((this[offset + 3].toInt() and 0xFF) shl 24))
    }

    /**
     * 从字节数组读取小端序Long。
     */
    private fun ByteArray.readLongLE(offset: Int): Long {
        return ((this[offset].toLong() and 0xFF) or
                ((this[offset + 1].toLong() and 0xFF) shl 8) or
                ((this[offset + 2].toLong() and 0xFF) shl 16) or
                ((this[offset + 3].toLong() and 0xFF) shl 24) or
                ((this[offset + 4].toLong() and 0xFF) shl 32) or
                ((this[offset + 5].toLong() and 0xFF) shl 40) or
                ((this[offset + 6].toLong() and 0xFF) shl 48) or
                ((this[offset + 7].toLong() and 0xFF) shl 56))
    }

    /**
     * End of Central Directory记录。
     */
    private data class EOCDRecord(
        val diskNumber: Int,
        val centralDirDisk: Int,
        val diskEntries: Int,
        val totalEntries: Int,
        val centralDirSize: Long,
        val centralDirOffset: Long,
        val commentLength: Int
    )

    /**
     * ZIP64 Extra Field信息。
     */
    private data class Zip64ExtraField(
        val uncompressedSize: Long,
        val compressedSize: Long,
        val localHeaderOffset: Long
    )
}
