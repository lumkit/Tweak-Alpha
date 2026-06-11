package io.github.lumkit.tweak.model

import kotlinx.serialization.Serializable

/**
 * CPU状态
 * @property number CPU编号
 * @property enabled 是否启用
 * @property currentFrequency 当前频率
 * @property maxFrequency 最大频率
 * @property minFrequency 最小频率
 * @property loadRate 负载率
 */
@Serializable
data class CpuState(
    val number: Int,
    val enabled: Boolean,
    val currentFrequency: Long,
    val maxFrequency: Long,
    val minFrequency: Long,
    val loadRate: Float? = null,
    val governor: String,
)
