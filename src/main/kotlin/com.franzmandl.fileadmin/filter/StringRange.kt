package com.franzmandl.fileadmin.filter

data class StringRange<P>(
    val value: String,
    val first: Int,
    val last: Int,
    val payload: P,
)