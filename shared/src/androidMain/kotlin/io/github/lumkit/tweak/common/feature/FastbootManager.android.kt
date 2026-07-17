package io.github.lumkit.tweak.common.feature

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import io.github.lumkit.tweak.adlib.client.FastbootClient
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.feature.FastbootManager.devices
import io.github.lumkit.tweak.common.utils.NativeFileBackend
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.openPrivilegedReadOnlyFd
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.asNativeFileBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.coroutines.resume
import io.github.lumkit.tweak.adlib.FastbootManager as AdlibFastbootManager
import io.github.lumkit.tweak.adlib.model.FastbootDevice as AdlibFastbootDevice

private const val TAG = "FastbootManager"

/** USB 授权结果广播的自定义 Action。 */
private const val ACTION_USB_PERMISSION = "io.github.lumkit.tweak.USB_PERMISSION"

/**
 * Android 平台的 [FastbootManager] 实现，基于 adlib + USB Host API。
 *
 * 通过监听系统的 [UsbManager.ACTION_USB_DEVICE_ATTACHED] /
 * [UsbManager.ACTION_USB_DEVICE_DETACHED] 广播以及自定义的授权广播，
 * 实时维护 [devices]。
 */
actual object FastbootManager {

    private val context: Context get() = application.applicationContext

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val delegate: AdlibFastbootManager by lazy { AdlibFastbootManager(context) }

    private val _devices = MutableStateFlow<List<FastbootDevice>>(emptyList())
    actual val devices: StateFlow<List<FastbootDevice>> = _devices.asStateFlow()

    /** 保存底层 adlib 设备，key 为 deviceName，供命令执行时回连真实的 UsbDevice。 */
    private val nativeDevices = mutableMapOf<String, AdlibFastbootDevice>()

    /** 等待授权结果的回调，key 为 deviceName。 */
    private val permissionCallbacks = mutableMapOf<String, (Boolean) -> Unit>()

    @Volatile
    private var monitoring: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh()

                ACTION_USB_PERMISSION -> {
                    val device = intent.usbDeviceExtra()
                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val name = device?.deviceName
                    logD("USB permission result: device=$name, granted=$granted", TAG)
                    if (name != null) {
                        permissionCallbacks.remove(name)?.invoke(granted)
                    }
                    refresh()
                }
            }
        }
    }

    actual fun startMonitor() {
        if (monitoring) return
        monitoring = true

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        logD("Fastboot monitor started", TAG)
        refresh()
    }

    actual fun stopMonitor() {
        if (!monitoring) return
        monitoring = false
        runCatching { context.unregisterReceiver(receiver) }
            .onFailure { logE(it.stackTraceToString(), tag = TAG) }
        logD("Fastboot monitor stopped", TAG)
    }

    actual fun refresh() {
        val native = runCatching { delegate.listDevices() }
            .onFailure { logE(it.stackTraceToString(), tag = TAG) }
            .getOrDefault(emptyList())

        synchronized(nativeDevices) {
            nativeDevices.clear()
            native.forEach { nativeDevices[it.deviceName] = it }
        }

        _devices.value = native.map { it.toCommon() }
        logD("Fastboot devices refreshed: ${_devices.value.size}", TAG)
    }

    actual fun hasPermission(device: FastbootDevice): Boolean {
        val native = device.native() ?: return false
        return delegate.hasPermission(native)
    }

    actual suspend fun requestPermission(device: FastbootDevice): Boolean {
        if (!monitoring) startMonitor()

        val native = device.native() ?: return false
        if (delegate.hasPermission(native)) return true

        return suspendCancellableCoroutine { continuation ->
            val name = device.deviceName
            permissionCallbacks[name] = { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
            continuation.invokeOnCancellation { permissionCallbacks.remove(name) }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags,
            )
            usbManager.requestPermission(native.usbDevice, pendingIntent)
        }
    }

    actual suspend fun getVar(device: FastbootDevice, variable: String): String =
        withClient(device) { it.getVar(variable) }

    actual suspend fun getAllVars(device: FastbootDevice): Map<String, String> =
        withClient(device) { it.getAllVars() }

    actual suspend fun flash(
        device: FastbootDevice,
        partition: String,
        image: ByteArray,
        onProgress: ((sent: Int, total: Int) -> Unit)?,
    ) = ByteArrayInputStream(image).use { input ->
        flash(device, partition, input, image.size.toLong()) { sent, total ->
            onProgress?.invoke(sent.toInt(), total.toInt())
        }
    }

    suspend fun flash(
        device: FastbootDevice,
        partition: String,
        input: InputStream,
        size: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ) = withClient(device) { it.flash(partition, input, size, onProgress) }

    actual suspend fun flash(
        device: FastbootDevice,
        partition: String,
        imagePath: String,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ) = openReadOnlyStream(imagePath).use { input ->
        withClient(device) { it.flash(partition, input.channel, input.channel.size(), onProgress) }
    }

    actual suspend fun erase(device: FastbootDevice, partition: String) =
        withClient(device) { it.erase(partition) }

    actual suspend fun setActiveSlot(device: FastbootDevice, slot: String) =
        withClient(device) { it.setActiveSlot(slot) }

    actual suspend fun reboot(device: FastbootDevice) =
        withClient(device) { it.reboot() }.let { }

    actual suspend fun rebootBootloader(device: FastbootDevice) =
        withClient(device) { it.rebootBootloader() }.let { }

    actual suspend fun runCommand(device: FastbootDevice, command: String): String =
        withClient(device) { it.runSimpleCommand(command).payload }

    suspend fun <R> withSession(
        device: FastbootDevice,
        block: suspend (FastbootClient) -> R,
    ): R = withClient(device, block)

    /**
     * 以“连接 -> 执行 -> 关闭”的完整生命周期执行一段命令逻辑。
     * 所有 USB 阻塞调用切换到 IO 线程执行。
     */
    private suspend fun <R> withClient(
        device: FastbootDevice,
        block: suspend (FastbootClient) -> R,
    ): R = withContext(Dispatchers.IO) {
        val native = device.native()
            ?: throw IllegalStateException("设备已断开：${device.deviceName}")
        val client = runCatching { delegate.connect(native) }
            .getOrElse { throwable ->
                logE(throwable.stackTraceToString(), throwable, TAG)
                throw IllegalStateException("无法连接 Fastboot 设备：${device.deviceName}", throwable)
            }
        try {
            block(client)
        } finally {
            runCatching { client.close() }
                .onFailure { logE(it.stackTraceToString(), it, TAG) }
        }
    }

    private fun FastbootDevice.native(): AdlibFastbootDevice? =
        synchronized(nativeDevices) { nativeDevices[deviceName] }

    private fun AdlibFastbootDevice.toCommon(): FastbootDevice = FastbootDevice(
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        serialNumber = serialNumber,
        hasPermission = runCatching { delegate.hasPermission(this) }.getOrDefault(false),
    )

    private suspend fun openReadOnlyStream(path: String): ParcelFileDescriptor.AutoCloseInputStream {
        val backend = resolvePrivilegedBackend(path)
        val pfd = openPrivilegedReadOnlyFd(backend, path)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    private suspend fun resolvePrivilegedBackend(path: String): NativeFileBackend {
        val runtimeMode = GlobalViewModel.runtimeModeState.filterNotNull().first()
        val backend = runtimeMode.asNativeFileBackend()
        require(backend != NativeFileBackend.User) {
            "当前运行模式不支持通过特权 Binder 打开文件 FD: $path"
        }
        return backend
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}
