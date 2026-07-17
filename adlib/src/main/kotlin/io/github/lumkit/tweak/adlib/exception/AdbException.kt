package io.github.lumkit.tweak.adlib.exception

open class AdbException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class AdbTransportException(
    message: String,
    cause: Throwable? = null,
) : AdbException(message, cause)

class AdbProtocolException(
    message: String,
    cause: Throwable? = null,
) : AdbException(message, cause)

class AdbSideloadException(
    message: String,
    cause: Throwable? = null,
) : AdbException(message, cause)

class AdbDeviceNotFoundException(
    message: String = "No adb device found",
) : AdbException(message)
