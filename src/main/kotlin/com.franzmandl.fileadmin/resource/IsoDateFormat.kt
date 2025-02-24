package com.franzmandl.fileadmin.resource

import org.springframework.format.annotation.DateTimeFormat

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
annotation class IsoDateFormat