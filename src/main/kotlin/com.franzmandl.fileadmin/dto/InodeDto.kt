package com.franzmandl.fileadmin.dto

import com.franzmandl.fileadmin.vfs.SafePath
import kotlinx.serialization.Serializable

@Serializable
data class InodeDto(
    val errors: List<String>,
    val friendlyName: String?,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val isRoot: Boolean,
    val isRunLast: Boolean,
    val isTimeDirectory: Boolean,
    val isVirtual: Boolean,
    val item: ItemDto?,
    val lastModifiedMilliseconds: Long?,
    val link: LinkDto?,
    val localPath: String?,
    val mimeType: String,
    val operation: OperationDto,
    val parentOperation: OperationDto?,
    val path: SafePath,
    val realPath: SafePath,
    val size: Long?,
    val task: Task?,
)