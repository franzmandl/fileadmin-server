package com.franzmandl.fileadmin.dto

import kotlinx.serialization.Serializable

@Serializable
data class OperationDto(
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
    val canInodeToDirectory: Boolean,
    val canInodeToFile: Boolean,
)