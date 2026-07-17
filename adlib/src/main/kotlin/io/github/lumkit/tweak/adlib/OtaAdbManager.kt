package io.github.lumkit.tweak.adlib

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import io.github.lumkit.tweak.adlib.client.OtaAdbClient
import io.github.lumkit.tweak.adlib.exception.AdbDeviceNotFoundException
import io.github.lumkit.tweak.adlib.model.AdbDevice
import io.github.lumkit.tweak.adlib.transport.UsbAdbTransport

class OtaAdbManager(context: Context) {

    private val usbManager: UsbManager =
        context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager

    fun listDevices(): List<AdbDevice> =
        usbManager.deviceList.values
            .filter { UsbAdbTransport.isAdbDevice(it) }
            .map { AdbDevice.from(it) }

    fun hasPermission(device: AdbDevice): Boolean =
        usbManager.hasPermission(device.usbDevice)

    fun connect(device: AdbDevice): OtaAdbClient =
        connect(device.usbDevice)

    fun connect(device: UsbDevice): OtaAdbClient {
        val transport = UsbAdbTransport.open(usbManager, device)
        return OtaAdbClient(transport)
    }

    fun connectFirst(): OtaAdbClient {
        val device = listDevices().firstOrNull()
            ?: throw AdbDeviceNotFoundException()
        return connect(device)
    }
}
