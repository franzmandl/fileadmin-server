package com.franzmandl.fileadmin.model

import com.franzmandl.fileadmin.vfs.SafePath
import com.franzmandl.fileadmin.vfs.UnsafePath
import kotlinx.serialization.Serializable

@Serializable
data class LinkModel(
    val originalTarget: UnsafePath,
    val target: SafePath,
)