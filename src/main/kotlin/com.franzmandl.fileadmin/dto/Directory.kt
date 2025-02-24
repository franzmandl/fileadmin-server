package com.franzmandl.fileadmin.dto

import kotlinx.serialization.Serializable

@Serializable
data class Directory(
    val canSearch: Boolean,
    val children: List<InodeDto>,
    val errors: List<String>,
    val inode: InodeDto,
    val nameCursorPosition: Int?,
    val newInodeTemplate: NewInode,
)