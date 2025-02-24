package com.franzmandl.fileadmin.dto.config

import com.franzmandl.fileadmin.filter.SystemTags
import com.franzmandl.fileadmin.vfs.UnsafePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FilterVersioned : ConfigRoot

@Serializable
@SerialName("FilterVersion1")
data class FilterVersion1(
    val enabled: Boolean? = null,
    override val imports: List<UnsafePath?>? = null,
    val input: List<InputVersioned?>? = null,
    val lostAndFound: String? = ConfigConstant.undefinedString,
    val operations: List<OperationVersioned?>? = null,
    val output: OutputVersioned? = null,
    val relationships: List<RelationshipDefinitionVersioned?>? = null,
    val scanModes: Map<CommandId, ScanModeVersioned>? = null,
    val systemTags: SystemTagsVersioned? = null,
    val tags: List<TagVersioned?>? = null,
) : FilterVersioned

@Serializable
enum class CommandId {
    Add,
    Delete,
    FilterItems,
    FirstScanItems,
    ForceScanItems,
    GetAllTags,
    GetDirectory,
    GetInode,
    GetSuggestion,
    GetSystemRoot,
    GetUnknownTags,
    GetUnusedTags,
    Move,
    MoveTag,
    Rename,
    RequiresAction,
    ToDirectory,
    ToFile,
}

@Serializable
sealed interface ScanModeVersioned

@Serializable
@SerialName("ScanModeVersion1")
data class ScanModeVersion1(
    val clearUnknown: Boolean? = null,
    val enabled: Boolean? = null,
    val onlyIfDirty: Boolean? = null,
    val ignoreLastModified: Boolean? = null,
    val ignoreScannedInputs: Boolean? = null,
    val lastModifiedEnabled: Boolean? = null,
    val markDirty: Boolean? = null,
    val quickBypass: Boolean? = null,
    val recomputeItems: Boolean? = null,
) : ScanModeVersioned

@Serializable
sealed interface SystemTagsVersioned

@Serializable
@SerialName("SystemTagsVersion1")
data class SystemTagsVersion1(
    override val directory: String? = null,
    override val emptyContent: String? = null,
    override val emptyName: String? = null,
    override val emptyParentPath: String? = null,
    override val file: String? = null,
    override val input: String? = null,
    override val lostAndFound: String? = null,
    override val prune: String? = null,
    override val task: String? = null,
    override val taskDone: String? = null,
    override val unknown: String? = null,
) : SystemTagsVersioned, SystemTags<String?>

@Serializable
sealed interface InputVersioned

@Serializable
@SerialName("InputVersion1")
data class InputVersion1(
    val automaticInputTag: Boolean? = null,
    val automaticTags: List<String>? = null,
    val comment: String? = null,
    val condition: PathConditionVersioned? = null,
    val contentCondition: PathConditionVersioned? = null,
    val enabled: Boolean? = null,
    val inodeTag: InodeTagVersioned? = null,
    val lostAndFound: String? = ConfigConstant.undefinedString,
    val name: String? = null,
    val path: UnsafePath? = null,
    val paths: List<UnsafePath>? = null,
    val pruneNames: Set<String>? = null,
    val scanNameForTags: Boolean? = null,
    val scanParentPathForTags: Boolean? = null,
) : InputVersioned

@Serializable
sealed interface OutputVersioned

@Serializable
@SerialName("OutputDirectoryVersion1")
data class OutputDirectoryVersion1(
    val autoIntersect: Boolean? = null,
    val hierarchicalTags: Boolean? = null,
    val path: UnsafePath,
    val rootTagsMinPriority: Int? = null,
) : OutputVersioned

@Serializable
sealed interface TagVersioned

@Serializable
@SerialName("TagVersion1")
data class TagVersion1(
    val name: String,
    val canRename: Boolean? = null,
    val children: List<TagVersioned?>? = null,
    val comment: String? = null,
    val descendantsRules: Set<TagRuleVersion1>? = null,
    val enabled: Boolean? = null,
    val exclusive: Boolean? = null,
    val exists: Boolean? = null,
    val first: Boolean? = null,
    val implyDescendants: Boolean? = null,
    val isA: List<String>? = null,
    val overrideParents: List<String>? = null,
    val parents: List<String>? = null,
    val placeholder: Boolean? = null,
    val priority: Int? = null,
    val relationships: Map<String, List<TagVersioned?>>? = null,
    val spread: Boolean? = null,
    val suggestMinimumLength: Int? = null,
) : TagVersioned

@Serializable
enum class TagRuleVersion1 {
    IsANonNull,
    ParentsNonNull,
}

@Serializable
sealed interface RelationshipDefinitionVersioned

@Serializable
@SerialName("RelationshipDefinitionVersion1")
data class RelationshipDefinitionVersion1(
    val addSubjectAsChild: Boolean? = null,
    val addSubjectAsParent: Boolean? = null,
    val name: String,
    val roots: List<String>? = null,
    val template: TagVersioned,
) : RelationshipDefinitionVersioned

@Serializable
sealed interface OperandVersioned {
    val enabled: Boolean?
    val suggestMinimumLength: Int?
}

@Serializable
sealed interface TagOperandVersioned : OperandVersioned {
    val name: String
    val parent: Boolean?
    val self: Boolean?
}

@Serializable
@SerialName("ChildrenOperandVersion1")
data class ChildrenOperandVersion1(
    override val enabled: Boolean? = null,
    override val name: String,
    override val parent: Boolean? = null,
    override val self: Boolean? = null,
    override val suggestMinimumLength: Int? = null,
) : TagOperandVersioned

@Serializable
@SerialName("DescendantOperandVersion1")
data class DescendantOperandVersion1(
    override val enabled: Boolean? = null,
    override val name: String,
    override val parent: Boolean? = null,
    override val self: Boolean? = null,
    override val suggestMinimumLength: Int? = null,
) : TagOperandVersioned

@Serializable
@SerialName("LeafOperandVersion1")
data class LeafOperandVersion1(
    override val enabled: Boolean? = null,
    override val name: String,
    override val parent: Boolean? = null,
    override val suggestMinimumLength: Int? = null,
) : TagOperandVersioned {
    override val self: Boolean = false
}

@Serializable
@SerialName("SelfOperandVersion1")
data class SelfOperandVersion1(
    override val enabled: Boolean? = null,
    override val name: String,
    override val parent: Boolean? = null,
    override val suggestMinimumLength: Int? = null,
) : TagOperandVersioned {
    override val self: Boolean = true
}

@Serializable
@SerialName("TextOperandVersion1")
data class TextOperandVersion1(
    override val enabled: Boolean? = null,
    val text: String,
    override val suggestMinimumLength: Int? = null,
) : OperandVersioned

@Serializable
sealed interface OperationVersioned

@Serializable
@SerialName("OperationVersion1")
data class OperationVersion1(
    val enabled: Boolean? = null,
    val operands: List<OperandVersioned?>,
    val parents: List<String>? = null,
) : OperationVersioned

@Serializable
sealed interface InodeTagVersioned

@Serializable
@SerialName("InodeTagVersion1")
data class InodeTagVersion1(
    val enabled: Boolean? = null,
    val parts: List<InodeTagPartVersioned?>? = null,
) : InodeTagVersioned

@Serializable
sealed interface InodeTagPartVersioned

@Serializable
@SerialName("InodeTagPartVersion1")
data class InodeTagPartVersion1(
    val checkReplacement: Boolean? = null,
    val index: Int,
    val pattern: String,
    val prependParent: Boolean? = null,
    val suggestMinimumLength: Int? = null,
    val suggestNameReplacement: String? = null,
    val template: TagVersioned,
) : InodeTagPartVersioned