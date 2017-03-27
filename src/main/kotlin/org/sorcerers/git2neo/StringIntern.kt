package org.sorcerers.git2neo

import java.util.*

/**
 * Created by vovak on 09/11/16.
 */
class StringIntern {
    val map: MutableMap<String, String> = HashMap()
    fun intern(str: String): String {
        if (str in map) return map[str]!!
        map[str] = str
        return str
    }
}