package com.franzmandl.fileadmin.model.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class NewInodeVersioned {
    abstract val basename: String?
    abstract val isFile: Boolean?
}

@Serializable
@SerialName("NewInodeVersion1")
data class NewInodeVersion1(
    override val basename: String? = null,
    override val isFile: Boolean? = null,
) : NewInodeVersioned()