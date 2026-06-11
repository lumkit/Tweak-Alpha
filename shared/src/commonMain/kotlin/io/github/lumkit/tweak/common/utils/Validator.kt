package io.github.lumkit.tweak.common.utils

object Validator {

    /**
     * 验证字符串是否有效（非空且长度大于3）。
     *
     * @param value 待验证的字符串。
     * @return 如果有效则返回true，否则返回false。
     */
    fun isValid(value: String?): Boolean {
        return !value.isNullOrBlank() && value.length > 3
    }
}
