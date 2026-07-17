package io.github.lumkit.tweak.common.feature

import io.github.lumkit.tweak.common.feature.FastbootManager.devices
import io.github.lumkit.tweak.common.feature.FastbootManager.flash
import io.github.lumkit.tweak.common.feature.FastbootManager.getVar
import io.github.lumkit.tweak.common.feature.FastbootManager.requestPermission
import io.github.lumkit.tweak.common.feature.FastbootManager.startMonitor
import kotlinx.coroutines.flow.StateFlow

/**
 * 描述一个通过 OTG 连接的 Fastboot 设备（平台无关模型）。
 *
 * 该模型是对 adlib 中 `io.github.lumkit.tweak.adlib.model.FastbootDevice` 的
 * commonMain 映射，剥离了 Android `UsbDevice` 等平台类型，便于在 commonMain 的
 * UI / ViewModel 层直接消费。
 *
 * @param deviceName    设备节点名，作为唯一标识（用于回连底层设备）。
 * @param vendorId      USB Vendor ID。
 * @param productId     USB Product ID。
 * @param serialNumber  设备序列号，未授权时可能为空。
 * @param hasPermission 当前是否已获得访问该设备的授权。
 */
data class FastbootDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
    val hasPermission: Boolean,
)

/**
 * commonMain 对外统一的 Fastboot 门面。
 *
 * 设备发现与命令执行的真正实现位于各平台的 `actual`（Android 侧基于 adlib +
 * USB Host API 实现）。
 *
 * ### 实时设备列表
 * [devices] 是一个可观察的 [StateFlow]，在调用 [startMonitor] 后会随 USB 设备的
 * 插拔、授权状态变化而实时更新。典型用法：
 *
 * ```kotlin
 * // 进入页面时开始监听
 * FastbootManager.startMonitor()
 * val devices by FastbootManager.devices.collectAsState()
 * // 离开页面时停止监听
 * FastbootManager.stopMonitor()
 * ```
 *
 * ### 命令执行
 * 命令类方法（[getVar]、[flash] 等）内部会自动完成“连接 -> 执行 -> 关闭”的生命周期，
 * 调用前需确保已通过 [requestPermission] 获得设备授权，否则会抛出异常。
 */
expect object FastbootManager {

    /**
     * 实时的 Fastboot 设备列表。
     *
     * 仅在 [startMonitor] 生效期间保持更新；未开始监听时为最近一次刷新的快照或空列表。
     */
    val devices: StateFlow<List<FastbootDevice>>

    /**
     * 开始监听 USB 设备插拔与授权变化，并立即刷新一次 [devices]。
     *
     * 重复调用是幂等的。
     */
    fun startMonitor()

    /**
     * 停止监听。停止后 [devices] 不再随设备插拔变化。
     */
    fun stopMonitor()

    /**
     * 主动刷新一次设备列表（同步）。
     */
    fun refresh()

    /**
     * 判断是否已获得访问该设备的权限。
     */
    fun hasPermission(device: FastbootDevice): Boolean

    /**
     * 请求访问该设备的权限，挂起直到用户授权对话框返回结果。
     *
     * @return 是否授权成功；若设备已断开则返回 false。
     */
    suspend fun requestPermission(device: FastbootDevice): Boolean

    /**
     * 读取设备变量，例如 `product`、`serialno`、`unlocked`、`current-slot` 等。
     */
    suspend fun getVar(device: FastbootDevice, variable: String): String

    /**
     * 获取所有设备变量（`getvar:all`）。
     */
    suspend fun getAllVars(device: FastbootDevice): Map<String, String>

    /**
     * 将 [image] 刷写到指定 [partition] 分区。
     *
     * @param onProgress 数据传输进度回调（已发送字节数, 总字节数）。
     */
    suspend fun flash(
        device: FastbootDevice,
        partition: String,
        image: ByteArray,
        onProgress: ((sent: Int, total: Int) -> Unit)? = null,
    )

    suspend fun flash(
        device: FastbootDevice,
        partition: String,
        imagePath: String,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    )

    /**
     * 擦除指定分区。
     */
    suspend fun erase(device: FastbootDevice, partition: String)

    /**
     * 设置当前活动 slot（A/B 设备），[slot] 取值 `a` 或 `b`。
     */
    suspend fun setActiveSlot(device: FastbootDevice, slot: String)

    /**
     * 重启到系统。
     */
    suspend fun reboot(device: FastbootDevice)

    /**
     * 重启到 bootloader。
     */
    suspend fun rebootBootloader(device: FastbootDevice)

    /**
     * 执行一条自定义的、仅关心成功与否的 Fastboot 命令，返回设备的 payload 文本。
     */
    suspend fun runCommand(device: FastbootDevice, command: String): String
}
