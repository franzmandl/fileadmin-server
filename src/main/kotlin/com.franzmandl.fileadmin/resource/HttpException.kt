package com.franzmandl.fileadmin.resource

class HttpException(val status: Int, message: String?) : RuntimeException(message)
