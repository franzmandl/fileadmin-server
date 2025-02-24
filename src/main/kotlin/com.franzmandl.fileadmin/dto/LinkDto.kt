package com.franzmandl.fileadmin.dto

import com.franzmandl.fileadmin.vfs.SafePath
import com.franzmandl.fileadmin.vfs.UnsafePath
import kotlinx.serialization.Serializable

@Serializable
data class LinkDto(
    val originalTarget: UnsafePath,
    val target: SafePath,
)