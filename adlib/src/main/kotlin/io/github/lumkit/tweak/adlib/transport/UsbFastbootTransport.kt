package io.github.lumkit.tweak.adlib.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import io.github.lumkit.tweak.adlib.exception.FastbootTransportException
import kotlin.math.min

/**
 * 基于 Android USB Host API 的 [FastbootTransport] 实现。
 *
 * 架构位置：
 * ```
 * FastbootTransport
 *        │
 * Android USB Host API           <-- UsbManager / UsbInterface / UsbEndpoint
 *        │
 * UsbDeviceConnection.bulkTransfer()   <-- 实际的字节收发
 *        │
 * Fastboot Device
 * ```
 *
 * Fastboot 使用 USB 的批量（bulk）传输端点，接口类为 `0xFF`（厂商自定义）、
 * 子类 `0x42`、协议 `0x03`。构造时会自动定位该接口及其 IN/OUT 端点并声明接口。
 */
class UsbFastbootTransport private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
    private val maxTransferSize: Int,
) : FastbootTransport {

    @Volatile
    private var closed: Boolean = false

    override val isConnected: Boolean
        get() = !closed

    override fun send(data: ByteArray, offset: Int, length: Int, timeoutMillis: Int): Int {
        checkOpen()
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "Invalid send range: offset=$offset length=$length size=${data.size}"
        }
        var totalSent = 0
        var currentOffset = offset
        var remaining = length
        while (remaining > 0) {
            val chunk = min(remaining, maxTransferSize)
            val transferred = connection.bulkTransfer(
                endpointOut,
                data,
                currentOffset,
                chunk,
                timeoutMillis,
            )
            if (transferred < 0) {
                throw FastbootTransportException(
                    "Failed to send data over USB bulk endpoint (requested=$chunk, sent=$totalSent/$length)",
                )
            }
            if (transferred == 0) {
                throw FastbootTransportException("USB send stalled (sent=$totalSent/$length)")
            }
            totalSent += transferred
            currentOffset += transferred
            remaining -= transferred
        }
        return totalSent
    }

    override fun receive(buffer: ByteArray, timeoutMillis: Int): Int {
        checkOpen()
        val transferred = connection.bulkTransfer(
            endpointIn,
            buffer,
            0,
            min(buffer.size, maxTransferSize),
            timeoutMillis,
        )
        if (transferred < 0) {
            throw FastbootTransportException("Failed to receive data over USB bulk endpoint")
        }
        return transferred
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { connection.releaseInterface(usbInterface) }
        runCatching { connection.close() }
    }

    private fun checkOpen() {
        if (closed) {
            throw FastbootTransportException("Transport already closed")
        }
    }

    companion object {

        /** Fastboot 接口标识：厂商自定义类。 */
        private const val FASTBOOT_INTERFACE_CLASS = UsbConstants.USB_CLASS_VENDOR_SPEC // 0xFF
        private const val FASTBOOT_INTERFACE_SUBCLASS = 0x42
        private const val FASTBOOT_INTERFACE_PROTOCOL = 0x03

        /**
         * Linux usbfs / Android USB Host 长期稳定上限。
         * targetSdk >= P 虽不再强制截断，但多数内核仍对更大的单次 URB 不稳定。
         */
        private const val MAX_USBFS_BUFFER_SIZE = 16 * 1024

        /**
         * 判断给定 USB 设备是否暴露了 Fastboot 接口。
         */
        fun isFastbootDevice(device: UsbDevice): Boolean =
            findFastbootInterface(device) != null

        /**
         * 打开设备并建立 Fastboot 传输通道。
         *
         * 调用前必须已经通过 [UsbManager.requestPermission] 获得该设备的访问授权。
         *
         * @throws FastbootTransportException 当设备不是 Fastboot 设备、无法打开或声明接口失败时。
         */
        fun open(usbManager: UsbManager, device: UsbDevice): UsbFastbootTransport {
            val usbInterface = findFastbootInterface(device)
                ?: throw FastbootTransportException("Device ${device.deviceName} does not expose a fastboot interface")

            var endpointIn: UsbEndpoint? = null
            var endpointOut: UsbEndpoint? = null
            for (i in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(i)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                when (endpoint.direction) {
                    UsbConstants.USB_DIR_IN -> endpointIn = endpoint
                    UsbConstants.USB_DIR_OUT -> endpointOut = endpoint
                }
            }
            if (endpointIn == null || endpointOut == null) {
                throw FastbootTransportException("Fastboot interface is missing bulk IN/OUT endpoints")
            }

            val connection = usbManager.openDevice(device)
                ?: throw FastbootTransportException("Unable to open device ${device.deviceName}, permission not granted?")

            if (!connection.claimInterface(usbInterface, true)) {
                connection.close()
                throw FastbootTransportException("Failed to claim fastboot interface on ${device.deviceName}")
            }

            val maxTransferSize = resolveMaxTransferSize(endpointOut.maxPacketSize)
            return UsbFastbootTransport(
                connection,
                usbInterface,
                endpointIn,
                endpointOut,
                maxTransferSize,
            )
        }

        private fun resolveMaxTransferSize(maxPacketSize: Int): Int {
            if (maxPacketSize <= 0) return MAX_USBFS_BUFFER_SIZE
            val aligned = (MAX_USBFS_BUFFER_SIZE / maxPacketSize) * maxPacketSize
            return aligned.coerceAtLeast(maxPacketSize)
        }

        private fun findFastbootInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == FASTBOOT_INTERFACE_CLASS &&
                    iface.interfaceSubclass == FASTBOOT_INTERFACE_SUBCLASS &&
                    iface.interfaceProtocol == FASTBOOT_INTERFACE_PROTOCOL
                ) {
                    return iface
                }
            }
            return null
        }
    }
}
