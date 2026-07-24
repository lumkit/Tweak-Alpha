package io.github.lumkit.tweak.common.utils

/**
 * 特权（Root / Shizuku）校验通过后的统一初始化入口。
 * 后续依赖特权的初始化逻辑都放在这里。
 */
object PrivilegedAppInitializer {

    suspend fun onPrivilegeReady() {
        AppsHelper.init()
    }
}
