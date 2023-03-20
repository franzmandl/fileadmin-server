package com.franzmandl.fileadmin.model

import com.franzmandl.fileadmin.vfs.SafePath
import kotlinx.serialization.Serializable

@Serializable
data class InodeModel(
    val error: String?,
    val filterHighlightTags: Collection<String>?,
    val filterOutputPath: SafePath?,
    val friendlyName: String?,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val isRoot: Boolean,
    val isRunLast: Boolean,
    val isVirtual: Boolean,
    val lastModified: Long?,
    val link: LinkModel?,
    val localPath: String?,
    val mimeType: String?,
    val operation: OperationModel,
    val parentOperation: OperationModel?,
    val path: SafePath,
    val realPath: SafePath,
    val size: Long?,
    val task: Task?,
)