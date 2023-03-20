package com.franzmandl.fileadmin.model.config

import com.franzmandl.fileadmin.vfs.UnsafePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class FilterVersioned : ConfigRoot() {
    abstract fun toLatestVersion(): FilterVersion1
}

@Serializable
@SerialName("FilterVersion1")
data class FilterVersion1(
    override val imports: List<UnsafePath?>? = null,
    val input: List<InputVersioned?>? = null,
    val output: OutputVersioned? = null,
    val tags: List<TagVersioned?>? = null,
) : FilterVersioned() {
    override fun toLatestVersion(): FilterVersion1 = this
}

@Serializable
sealed class InputVersioned {
    abstract fun toLatestVersion(): InputVersion1
}

@Serializable
@SerialName("InputVersion1")
data class InputVersion1(
    val automaticInputTag: Boolean? = null,
    val automaticTags: List<String>? = null,
    val comment: String? = null,
    val condition: ConditionVersioned? = null,
    val contentCondition: ConditionVersioned? = null,
    val enable: Boolean? = null,
    val path: UnsafePath,
    val pruneNames: Set<String>? = null,
) : InputVersioned() {
    override fun toLatestVersion(): InputVersion1 = this
}

@Serializable
sealed class OutputVersioned {
    abstract fun toLatestVersion(): OutputDirectoryVersion1
}

@Serializable
@SerialName("OutputDirectoryVersion1")
data class OutputDirectoryVersion1(
    val hierarchicalTags: Boolean? = null,
    val path: UnsafePath,
    val tagDirectory: String? = null,
    val tagFile: String? = null,
    val tagInput: String? = null,
    val tagLostAndFound: String? = null,
    val tagUnknown: String? = null,
) : OutputVersioned() {
    override fun toLatestVersion(): OutputDirectoryVersion1 = this
}

@Serializable
sealed class TagVersioned {
    abstract fun toLatestVersion(): TagVersion1
}

@Serializable
@SerialName("TagVersion1")
data class TagVersion1(
    val name: String,
    val canRename: Boolean? = null,
    val children: List<TagVersioned?>? = null,
    val comment: String? = null,
    val parents: List<String>? = null,
    val priority: Int? = null,
    val spread: Boolean? = null,
) : TagVersioned() {
    override fun toLatestVersion(): TagVersion1 = this
}