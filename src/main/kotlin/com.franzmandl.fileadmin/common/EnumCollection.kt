package com.franzmandl.fileadmin.common

import java.util.*
import kotlin.reflect.KClass

class EnumCollection<K : Enum<K>, V>(
    keyClass: KClass<K>,
    init: (K) -> V
) {
    private val map = EnumMap<K, V>(keyClass.java)

    init {
        for (key in keyClass.java.enumConstants) {
            map[key] = init(key)
        }
    }

    operator fun get(key: K): V =
        map[key] ?: error("""Uninitialized key "$key".""")
}