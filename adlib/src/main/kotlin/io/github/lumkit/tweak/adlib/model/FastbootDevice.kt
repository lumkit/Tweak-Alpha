package io.github.lumkit.tweak.adlib.model

import android.hardware.usb.UsbDevice

/**
 * 描述一个通过 OTG 连接的 Fastboot 设备。
 *
 * @param usbDevice 底层的 Android USB 设备对象。
 * @param vendorId  USB Vendor ID。
 * @param productId USB Product ID。
 * @param serialNumber 设备序列号，可能为空（未授权时无法读取）。
 */
data class FastbootDevice(
    val usbDevice: UsbDevice,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
) {
    val deviceName: String get() = usbDevice.deviceName

    companion object {
        fun from(usbDevice: UsbDevice): FastbootDevice = FastbootDevice(
            usbDevice = usbDevice,
            vendorId = usbDevice.vendorId,
            productId = usbDevice.productId,
            serialNumber = runCatching { usbDevice.serialNumber }.getOrNull(),
        )
    }
}
