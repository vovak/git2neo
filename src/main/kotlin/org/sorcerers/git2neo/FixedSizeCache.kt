package org.sorcerers.git2neo

/**
 * Created by vovak on 3/27/17.
 */
class FixedSizeCache<K,V>(val sizeLimit: Int) {
    private val map: MutableMap<K, V> = HashMap()

    fun containsKey(key: K) = map.containsKey(key)

    fun put(key: K, value: V): Unit {
        if (map.size > sizeLimit) map.clear()
        map[key] = value
    }

    fun get(key: K): V? {
        return map[key]
    }
}