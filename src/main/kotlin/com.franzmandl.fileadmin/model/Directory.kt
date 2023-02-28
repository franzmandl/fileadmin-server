package com.franzmandl.fileadmin.model

import kotlinx.serialization.Serializable

@Serializable
data class Directory(
    val canSearch: Boolean,
    val errors: List<String>,
    val inode: InodeModel,
    val inodes: List<InodeModel>,
    val nameCursorPosition: Int?,
    val newInodeTemplate: NewInode,
)