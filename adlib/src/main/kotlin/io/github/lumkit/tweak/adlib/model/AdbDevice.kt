package io.github.lumkit.tweak.adlib.model

import android.hardware.usb.UsbDevice

data class AdbDevice(
    val usbDevice: UsbDevice,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
) {
    val deviceName: String get() = usbDevice.deviceName

    companion object {
        fun from(usbDevice: UsbDevice): AdbDevice = AdbDevice(
            usbDevice = usbDevice,
            vendorId = usbDevice.vendorId,
            productId = usbDevice.productId,
            serialNumber = runCatching { usbDevice.serialNumber }.getOrNull(),
        )
    }
}
