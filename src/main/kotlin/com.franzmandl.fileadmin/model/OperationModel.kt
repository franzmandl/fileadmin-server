package com.franzmandl.fileadmin.model

import kotlinx.serialization.Serializable

@Serializable
data class OperationModel(
    val canDirectoryAdd: Boolean,
    val canDirectoryGet: Boolean,
    val canFileGet: Boolean,
    val canFileSet: Boolean,
    val canFileStream: Boolean,
    val canInodeCopy: Boolean,
    val canInodeDelete: Boolean,
    val canInodeMove: Boolean,
    val canInodeRename: Boolean,
    val canInodeShare: Boolean,
)