package com.franzmandl.fileadmin.common

sealed interface OptionalValue<T> {
    val hasValue: Boolean
    val nullableValue: T?
    val value: T

    class Fail<T>(
        private val message: String,
    ) : OptionalValue<T> {
        override val hasValue: Boolean = false
        override val nullableValue: T? = null
        override val value get(): T = throw HttpException.badRequest(message)
    }

    class Success<T>(
        override val value: T,
    ) : OptionalValue<T> {
        override val hasValue: Boolean = true
        override val nullableValue: T = value
    }
}