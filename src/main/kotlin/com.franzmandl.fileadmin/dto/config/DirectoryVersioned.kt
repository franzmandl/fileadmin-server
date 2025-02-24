package com.franzmandl.fileadmin.dto.config

import com.franzmandl.fileadmin.vfs.UnsafePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ConfigRoot {
    val imports: List<UnsafePath?>?
}

@Serializable
sealed interface ConfigValue : ConfigRoot

@Serializable
sealed interface DirectoryVersioned : ConfigRoot

@Serializable
@SerialName("DirectoryVersion1")
data class DirectoryVersion1(
    val filter: FilterVersioned? = null,
    override val imports: List<UnsafePath?>? = null,
    val tags: List<TagVersioned?>? = null,
    val values: List<ConfigValue?>? = null,
) : DirectoryVersioned

@Serializable
sealed interface DirectoryValueVersioned : ConfigValue

@Serializable
@SerialName("DirectoryValueVersion1")
data class DirectoryValueVersion1(
    val condition: PathConditionVersioned? = null,
    override val imports: List<UnsafePath?>? = null,
    val nameCursorPosition: Int? = null,
    val newInodeTemplate: NewInodeVersioned? = null,
    val runLast: RunLastVersioned? = null,
    val tasks: TasksVersioned? = null,
) : DirectoryValueVersioned

@Serializable
sealed interface NewInodeVersioned : ConfigValue

@Serializable
@SerialName("NewInodeVersion1")
data class NewInodeVersion1(
    val condition: PathConditionVersioned? = null,
    val isFile: Boolean? = null,
    val name: String? = null,
    override val imports: List<UnsafePath?>? = null,
) : NewInodeVersioned

@Serializable
sealed interface RunLastVersioned : ConfigValue

@Serializable
@SerialName("RunLastVersion1")
data class RunLastVersion1(
    val condition: PathConditionVersioned? = null,
    val enabled: Boolean? = null,
    override val imports: List<UnsafePath?>? = null,
) : RunLastVersioned

@Serializable
sealed interface TasksVersioned : ConfigValue

@Serializable
@SerialName("TasksVersion1")
data class TasksVersion1(
    val condition: PathConditionVersioned? = null,
    val enabled: Boolean? = null,
    override val imports: List<UnsafePath?>? = null,
) : TasksVersioned