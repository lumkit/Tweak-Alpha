package io.github.lumkit.tweak.sharednative

import android.os.Bundle

object NativeFileBundles {
    const val KEY_SUCCESS = "success"
    const val KEY_MESSAGE = "message"
    const val KEY_BOOLEAN = "boolean"
    const val KEY_LONG = "long"
    const val KEY_BYTES = "bytes"
    const val KEY_STRING = "string"
    const val KEY_STRING_LIST = "string_list"
    const val KEY_BUNDLE_LIST = "bundle_list"
    const val KEY_NAME = "name"
    const val KEY_IS_DIRECTORY = "is_directory"
    const val KEY_SIZE = "size"
    const val KEY_COMPRESSED_SIZE = "compressed_size"
    const val KEY_CRC = "crc"
    const val KEY_TIME = "time"
    const val KEY_OFFSET = "offset"

    @JvmStatic
    fun successUnit(): Bundle = Bundle().apply {
        putBoolean(KEY_SUCCESS, true)
    }

    @JvmStatic
    fun successBoolean(value: Boolean): Bundle = successUnit().apply {
        putBoolean(KEY_BOOLEAN, value)
    }

    @JvmStatic
    fun successLong(value: Long): Bundle = successUnit().apply {
        putLong(KEY_LONG, value)
    }

    @JvmStatic
    fun successBytes(value: ByteArray): Bundle = successUnit().apply {
        putByteArray(KEY_BYTES, value)
    }

    @JvmStatic
    fun successString(value: String): Bundle = successUnit().apply {
        putString(KEY_STRING, value)
    }

    @JvmStatic
    fun successStringList(value: List<String>): Bundle = successUnit().apply {
        putStringArrayList(KEY_STRING_LIST, ArrayList(value))
    }

    @JvmStatic
    fun successBundleList(value: List<Bundle>): Bundle = successUnit().apply {
        putParcelableArrayList(KEY_BUNDLE_LIST, ArrayList(value))
    }

    @JvmStatic
    fun failure(throwable: Throwable): Bundle = Bundle().apply {
        putBoolean(KEY_SUCCESS, false)
        putString(KEY_MESSAGE, throwable.message ?: throwable.toString())
    }
}
