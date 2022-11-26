package com.franzmandl.fileadmin.resource

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler


abstract class BaseResource {
    @ExceptionHandler(HttpException::class)
    fun respondHttpException(e: HttpException): ResponseEntity<String> {
        return ResponseEntity.status(e.status).body(e.message)
    }
}
