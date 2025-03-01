package com.franzmandl.fileadmin.dto

import com.franzmandl.fileadmin.vfs.SafePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Command

@Serializable
@SerialName("Add")
data class Add(
    val path: SafePath,
    val newInode: NewInode,
) : Command()

@Serializable
@SerialName("Delete")
data class Delete(
    val path: SafePath,
) : Command()

@Serializable
@SerialName("Move")
data class Move(
    val path: SafePath,
    val newPath: SafePath,
) : Command()

@Serializable
@SerialName("Rename")
data class Rename(
    val path: SafePath,
    val newName: String,
) : Command()

@Serializable
@SerialName("Share")
data class Share(
    val path: SafePath,
    val days: Int,
) : Command()

@Serializable
@SerialName("ToDirectory")
data class ToDirectory(
    val path: SafePath,
) : Command()

@Serializable
@SerialName("ToFile")
data class ToFile(
    val path: SafePath,
) : Command()