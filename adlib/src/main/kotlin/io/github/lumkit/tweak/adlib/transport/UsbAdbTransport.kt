package io.github.lumkit.tweak.adlib.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import io.github.lumkit.tweak.adlib.exception.AdbTransportException

class UsbAdbTransport private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
) : AdbTransport {

    @Volatile
    private var closed: Boolean = false

    override val isConnected: Boolean
        get() = !closed

    override fun send(data: ByteArray, offset: Int, length: Int, timeoutMillis: Int): Int {
        checkOpen()
        val transferred = connection.bulkTransfer(endpointOut, sliceIfNeeded(data, offset, length), length, timeoutMillis)
        if (transferred < 0) {
            throw AdbTransportException("Failed to send data over USB bulk endpoint")
        }
        return transferred
    }

    override fun receive(buffer: ByteArray, timeoutMillis: Int): Int {
        checkOpen()
        val transferred = connection.bulkTransfer(endpointIn, buffer, buffer.size, timeoutMillis)
        if (transferred < 0) {
            throw AdbTransportException("Failed to receive data over USB bulk endpoint")
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
            throw AdbTransportException("Transport already closed")
        }
    }

    private fun sliceIfNeeded(data: ByteArray, offset: Int, length: Int): ByteArray {
        return if (offset == 0) {
            data
        } else {
            data.copyOfRange(offset, offset + length)
        }
    }

    companion object {
        private const val ADB_INTERFACE_CLASS = UsbConstants.USB_CLASS_VENDOR_SPEC
        private const val ADB_INTERFACE_SUBCLASS = 0x42
        private const val ADB_INTERFACE_PROTOCOL = 0x01

        fun isAdbDevice(device: UsbDevice): Boolean =
            findAdbInterface(device) != null

        fun open(usbManager: UsbManager, device: UsbDevice): UsbAdbTransport {
            val usbInterface = findAdbInterface(device)
                ?: throw AdbTransportException("Device ${device.deviceName} does not expose an adb interface")

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
                throw AdbTransportException("Adb interface is missing bulk IN/OUT endpoints")
            }

            val connection = usbManager.openDevice(device)
                ?: throw AdbTransportException("Unable to open device ${device.deviceName}, permission not granted?")

            if (!connection.claimInterface(usbInterface, true)) {
                connection.close()
                throw AdbTransportException("Failed to claim adb interface on ${device.deviceName}")
            }

            return UsbAdbTransport(connection, usbInterface, endpointIn, endpointOut)
        }

        private fun findAdbInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == ADB_INTERFACE_CLASS &&
                    iface.interfaceSubclass == ADB_INTERFACE_SUBCLASS &&
                    iface.interfaceProtocol == ADB_INTERFACE_PROTOCOL
                ) {
                    return iface
                }
            }
            return null
        }
    }
}
