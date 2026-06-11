package io.github.lumkit.tweak.common.utils

object Cache {
    private val cacheMap = mutableMapOf<String, String>()

    /**
     * 检查缓存中是否存在指定的键。
     *
     * @param key 键名。
     * @return 如果存在则返回true，否则返回false。
     */
    fun hasKey(key: String): Boolean {
        return cacheMap.containsKey(key)
    }

    /**
     * 获取缓存中指定键的值。
     *
     * @param key 键名。
     * @return 如果存在则返回对应的值，否则返回null。
     */
    fun get(key: String): String? {
        return cacheMap[key]
    }

    /**
     * 在缓存中设置键值对。
     *
     * @param key   键名。
     * @param value 键值。
     */
    fun put(key: String, value: String) {
        cacheMap[key] = value
    }
}
