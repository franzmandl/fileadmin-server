package com.franzmandl.fileadmin.model

import kotlinx.serialization.Serializable

@Serializable
data class NewInode(
    val isFile: Boolean,
    val name: String,
)