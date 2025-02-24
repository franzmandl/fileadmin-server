package com.franzmandl.fileadmin.dto.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class PathConditionVersioned

@Serializable
sealed class AdvancedPathConditionVersioned : PathConditionVersioned()

@Serializable
sealed class SimplePathConditionVersioned : PathConditionVersioned()

@Serializable
@SerialName("PathConditionVersion1")
data class PathConditionVersion1(
    val components: List<PathComponentVersioned?>? = null,
    val finalComponent: FinalPathComponentVersioned? = null,
    val ignoreNonDirectories: Boolean? = null,
    val pruneConfigFiles: Boolean? = null,
    val pruneNames: Set<String>? = null,
    val root: RootPathComponentVersioned? = null,
) : AdvancedPathConditionVersioned()

@Serializable
sealed class PathComponentVersioned

@Serializable
@SerialName("PathComponentVersion1")
data class PathComponentVersion1(
    override val directoryNameGlob: String? = null,
    override val directoryNameRegex: String? = null,
    override val directoryPathRegex: String? = null,
    override val directoryWhitelist: Set<String>? = null,
    val enterNames: Set<String>? = null,
    override val fileNameGlob: String? = null,
    override val fileNameRegex: String? = null,
    override val filePathRegex: String? = null,
    override val fileWhitelist: Set<String>? = null,
    override val nameGlob: String? = null,
    override val nameRegex: String? = null,
    val ignoreNonDirectories: Boolean? = null,
    override val pathRegex: String? = null,
    val pruneConfigFiles: Boolean? = null,
    val pruneNames: Set<String>? = null,
    val time: Boolean? = null,
    override val whitelist: Set<String>? = null,
    val yield: Boolean? = null,
) : PathComponentVersioned(), HasPatterns

@Serializable
@SerialName("SimplePathConditionVersion1")
data class SimplePathConditionVersion1(
    override val directoryNameGlob: String? = null,
    override val directoryNameRegex: String? = null,
    override val directoryPathRegex: String? = null,
    override val directoryWhitelist: Set<String>? = null,
    val enterNames: Set<String>? = null,
    override val fileNameGlob: String? = null,
    override val fileNameRegex: String? = null,
    override val filePathRegex: String? = null,
    override val fileWhitelist: Set<String>? = null,
    override val nameGlob: String? = null,
    override val nameRegex: String? = null,
    /**
     * 0 ... The parent directory of the config file.
     * 1 ... Siblings of the config file and config file itself.
     * 2 ... And so forth.
     */
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    override val pathRegex: String? = null,
    val pruneConfigFiles: Boolean? = null,
    val pruneNames: Set<String>? = null,
    val time: Boolean? = null,
    override val whitelist: Set<String>? = null,
) : SimplePathConditionVersioned(), HasPatterns {
    companion object {
        val default = SimplePathConditionVersion1()
    }
}

@Serializable
sealed class FinalPathComponentVersioned

@Serializable
@SerialName("FinalPathComponentVersion1")
data class FinalPathComponentVersion1(
    override val directoryNameGlob: String? = null,
    override val directoryNameRegex: String? = null,
    override val directoryPathRegex: String? = null,
    override val directoryWhitelist: Set<String>? = null,
    override val fileNameGlob: String? = null,
    override val fileNameRegex: String? = null,
    override val filePathRegex: String? = null,
    override val fileWhitelist: Set<String>? = null,
    override val nameGlob: String? = null,
    override val nameRegex: String? = null,
    override val pathRegex: String? = null,
    val pruneConfigFiles: Boolean? = null,
    val pruneNames: Set<String>? = null,
    override val whitelist: Set<String>? = null,
) : FinalPathComponentVersioned(), HasPatterns

@Serializable
sealed class RootPathComponentVersioned

@Serializable
@SerialName("RootPathComponentVersion1")
data class RootPathComponentVersion1(
    override val directoryNameGlob: String? = null,
    override val directoryNameRegex: String? = null,
    override val directoryPathRegex: String? = null,
    override val directoryWhitelist: Set<String>? = null,
    override val fileNameGlob: String? = null,
    override val fileNameRegex: String? = null,
    override val filePathRegex: String? = null,
    override val fileWhitelist: Set<String>? = null,
    override val nameGlob: String? = null,
    override val nameRegex: String? = null,
    override val pathRegex: String? = null,
    override val whitelist: Set<String>? = null,
    val yield: Boolean? = null,
) : RootPathComponentVersioned(), HasPatterns {
    companion object {
        val default = RootPathComponentVersion1()
    }
}

interface HasCommonPattern {
    val nameGlob: String?
    val nameRegex: String?
    val pathRegex: String?
    val whitelist: Set<String>?

    object Default : HasCommonPattern {
        override val nameGlob = null
        override val nameRegex = null
        override val pathRegex = null
        override val whitelist = null
    }
}

interface HasDirectoryPattern {
    val directoryNameGlob: String?
    val directoryNameRegex: String?
    val directoryPathRegex: String?
    val directoryWhitelist: Set<String>?

    object Default : HasDirectoryPattern {
        override val directoryNameGlob = null
        override val directoryNameRegex = null
        override val directoryPathRegex = null
        override val directoryWhitelist = null
    }
}

interface HasFilePattern {
    val fileNameGlob: String?
    val fileNameRegex: String?
    val filePathRegex: String?
    val fileWhitelist: Set<String>?

    object Default : HasFilePattern {
        override val fileNameGlob = null
        override val fileNameRegex = null
        override val filePathRegex = null
        override val fileWhitelist = null
    }
}

interface HasPatterns : HasCommonPattern, HasDirectoryPattern, HasFilePattern {
    object Default : HasPatterns, HasCommonPattern by HasCommonPattern.Default, HasDirectoryPattern by HasDirectoryPattern.Default, HasFilePattern by HasFilePattern.Default
}