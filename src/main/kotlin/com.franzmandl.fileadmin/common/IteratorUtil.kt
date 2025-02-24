package com.franzmandl.fileadmin.common

import java.util.function.Consumer

object IteratorUtil {
    fun <T> nextOrNull(iterator: Iterator<T>): T? =
        if (iterator.hasNext()) iterator.next() else null

    fun <T> consumeUntil(iterator: Iterator<T>, terminal: T, consumer: Consumer<T>): Boolean {
        while (iterator.hasNext()) {
            val value = iterator.next()
            if (value == terminal) {
                return true
            }
            consumer.accept(value)
        }
        return false
    }
}