package com.franzmandl.fileadmin.common

fun interface ErrorHandler {
    fun onError(message: String): Nothing?

    companion object {
        val badRequest = ErrorHandler { throw HttpException.badRequest(it) }
        val error = ErrorHandler { error(it) }
        val noop = ErrorHandler { null }

        fun concatenate(errorHandler: ErrorHandler, prefix: String): ErrorHandler =
            ErrorHandler { errorHandler.onError("$prefix: $it") }
    }
}