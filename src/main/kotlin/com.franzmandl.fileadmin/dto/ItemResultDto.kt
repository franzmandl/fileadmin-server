package com.franzmandl.fileadmin.dto

import kotlinx.serialization.Serializable

@Serializable
data class ItemResultDto(
    val highlightTags: Collection<String>,
    val priority: Int?,
)