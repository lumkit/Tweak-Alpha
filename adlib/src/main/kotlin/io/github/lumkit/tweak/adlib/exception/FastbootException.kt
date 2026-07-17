package io.github.lumkit.tweak.adlib.exception

/**
 * Fastboot 操作过程中抛出的异常基类。
 */
open class FastbootException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * USB 传输层异常，例如无法打开设备、声明接口失败、读写超时等。
 */
class FastbootTransportException(
    message: String,
    cause: Throwable? = null,
) : FastbootException(message, cause)

/**
 * 设备返回 FAIL 响应时抛出。
 *
 * @param reason 设备在 FAIL 响应中携带的原因描述。
 */
class FastbootCommandException(
    val command: String,
    val reason: String,
) : FastbootException("Command \"$command\" failed: $reason")

/**
 * 未找到可用的 Fastboot 设备时抛出。
 */
class FastbootDeviceNotFoundException(
    message: String = "No fastboot device found",
) : FastbootException(message)
