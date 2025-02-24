package com.franzmandl.fileadmin.dto

import com.franzmandl.fileadmin.vfs.SafePath
import kotlinx.serialization.Serializable

@Serializable
data class ItemDto(
    val outputPath: SafePath,
    val result: ItemResultDto?,
    val tags: Collection<String>?,
    val time: String?,
)