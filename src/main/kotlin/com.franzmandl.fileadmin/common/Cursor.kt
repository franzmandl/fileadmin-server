package com.franzmandl.fileadmin.common

class Cursor<T>(
    current: T,
    private val inner: Iterator<T>,
) {
    var current = current
        private set
    var hasEnded = false
        private set

    fun proceed() {
        if (inner.hasNext()) {
            current = inner.next()
        } else {
            hasEnded = true
        }
    }

    companion object {
        fun <T> create(iterator: Iterator<T>): Cursor<T>? =
            if (iterator.hasNext()) Cursor(iterator.next(), iterator) else null

        fun <T> create(iterable: Iterable<T>): Cursor<T>? =
            create(iterable.iterator())
    }
}