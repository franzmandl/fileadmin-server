package com.franzmandl.fileadmin.model

import kotlinx.serialization.Serializable

@Serializable
data class NewInode(
    val basename: String,
    val isFile: Boolean,
)