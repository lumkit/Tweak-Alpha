package io.github.lumkit.tweak.adlib

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import io.github.lumkit.tweak.adlib.client.FastbootClient
import io.github.lumkit.tweak.adlib.exception.FastbootDeviceNotFoundException
import io.github.lumkit.tweak.adlib.model.FastbootDevice
import io.github.lumkit.tweak.adlib.transport.UsbFastbootTransport

/**
 * adlib 模块对外的统一入口，负责 OTG Fastboot 设备的发现与连接。
 *
 * 架构位置：
 * ```
 * Tweak
 *   │
 * FastbootManager  <-- 当前类：设备发现 + 建立连接的门面
 *   │
 * FastbootClient
 *   │
 * FastbootTransport (UsbFastbootTransport)
 *   │
 * Android USB Host API
 * ```
 *
 * 典型用法：
 * ```kotlin
 * val manager = FastbootManager(context)
 * val device = manager.listDevices().first()
 * // 通过 UsbManager.requestPermission 获取授权后：
 * manager.connect(device).use { client ->
 *     val product = client.getVar("product")
 * }
 * ```
 */
class FastbootManager(context: Context) {

    private val usbManager: UsbManager =
        context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * 列出当前已连接且暴露 Fastboot 接口的 USB 设备。
     */
    fun listDevices(): List<FastbootDevice> =
        usbManager.deviceList.values
            .filter { UsbFastbootTransport.isFastbootDevice(it) }
            .map { FastbootDevice.from(it) }

    /**
     * 判断是否已获得访问该设备的权限。
     */
    fun hasPermission(device: FastbootDevice): Boolean =
        usbManager.hasPermission(device.usbDevice)

    /**
     * 与设备建立 Fastboot 连接并返回可用的 [FastbootClient]。
     *
     * 调用前需确保已通过 [UsbManager.requestPermission] 获得授权，
     * 否则底层打开设备会失败。
     */
    fun connect(device: FastbootDevice): FastbootClient =
        connect(device.usbDevice)

    /**
     * 与设备建立 Fastboot 连接并返回可用的 [FastbootClient]。
     */
    fun connect(device: UsbDevice): FastbootClient {
        val transport = UsbFastbootTransport.open(usbManager, device)
        return FastbootClient(transport)
    }

    /**
     * 连接第一个可用的 Fastboot 设备。
     *
     * @throws FastbootDeviceNotFoundException 当没有可用设备时。
     */
    fun connectFirst(): FastbootClient {
        val device = listDevices().firstOrNull()
            ?: throw FastbootDeviceNotFoundException()
        return connect(device)
    }
}
