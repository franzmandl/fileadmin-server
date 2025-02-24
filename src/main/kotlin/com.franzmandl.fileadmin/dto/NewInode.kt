package com.franzmandl.fileadmin.dto

import kotlinx.serialization.Serializable

@Serializable
data class NewInode(
    val isFile: Boolean,
    val name: String,
)