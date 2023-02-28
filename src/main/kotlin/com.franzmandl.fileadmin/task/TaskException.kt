package com.franzmandl.fileadmin.task

sealed interface Payload<out T> {
    val value: T?
}

class TaskException(
    override val message: String,
) : Exception(), Payload<Nothing> {
    override val value: Nothing? = null
}

class Value<T>(
    override val value: T,
) : Payload<T>