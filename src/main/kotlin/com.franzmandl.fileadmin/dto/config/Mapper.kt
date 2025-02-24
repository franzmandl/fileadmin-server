package com.franzmandl.fileadmin.dto.config

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.vfs.PathCondition
import java.util.*

class Mapper(
    configFileName: String,
) {
    private val configFileNameSet = setOf(configFileName)

    fun fromVersioned(versioned: DirectoryVersioned): DirectoryVersion1 =
        when (versioned) {
            is DirectoryVersion1 -> versioned
        }

    fun fromVersioned(versioned: DirectoryValueVersioned): DirectoryValueVersion1 =
        when (versioned) {
            is DirectoryValueVersion1 -> versioned
        }

    fun fromVersioned(
        versioned: PathConditionVersioned,
        errorHandler: ErrorHandler,
        @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
        defaultMinDepth: Int = 1,
        ignoreNonDirectoriesComponent0: Boolean? = null,
        pruneContentRegex: Regex? = null,
        pruneNames: Set<String>? = null,
    ): PathCondition =
        when (versioned) {
            is PathConditionVersion1 -> fromPathConditionVersion1(
                versioned,
                ignoreNonDirectoriesComponent0 = ignoreNonDirectoriesComponent0,
                pruneContentRegex = pruneContentRegex,
                pruneNames = pruneNames,
            )

            is SimplePathConditionVersion1 -> fromSimplePathConditionVersion1(
                versioned, errorHandler,
                defaultMinDepth = defaultMinDepth,
                ignoreNonDirectoriesComponent0 = ignoreNonDirectoriesComponent0,
                pruneContentRegex = pruneContentRegex,
                pruneNames = pruneNames,
            )
        }

    private fun fromPathConditionVersion1(
        versioned: PathConditionVersion1,
        @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
        ignoreNonDirectoriesComponent0: Boolean?,
        pruneContentRegex: Regex?,
        pruneNames: Set<String>?,
    ): PathCondition {
        val versionedComponents = versioned.components ?: listOf()
        val lastNonNullIndex = if (versioned.finalComponent == null) versionedComponents.indexOfLast { it != null } else -1
        val ignoreNonDirectories = versioned.ignoreNonDirectories ?: false
        val components = LinkedList<PathCondition.Component>()
        for ((index, component) in versionedComponents.withIndex()) {
            when (component) {
                is PathComponentVersion1 -> {
                    val enterNames = component.enterNames ?: setOf()
                    val isTime = component.time == true
                    components += PathCondition.Component(
                        enterNames = enterNames,
                        ignoreNonDirectories = ignoreNonDirectoriesComponent0.takeIf { components.isEmpty() } ?: component.ignoreNonDirectories ?: ignoreNonDirectories,
                        index = components.size,
                        patterns = createPatterns(component),
                        pruneNames = getPruneNames(pruneNames, component.pruneNames, component.pruneConfigFiles),
                        time = isTime,
                        yield = component.yield ?: (index == lastNonNullIndex),
                    )
                }

                null -> Unit
            }
        }
        val latestRootComponent = when (versioned.root) {
            is RootPathComponentVersion1 -> versioned.root
            null -> null
        }
        return PathCondition(
            components = components,
            finalComponent = versioned.finalComponent?.let { component ->
                when (component) {
                    is FinalPathComponentVersion1 ->
                        PathCondition.Component(
                            enterNames = setOf(),
                            ignoreNonDirectories = true,
                            index = components.size,
                            patterns = createPatterns(component),
                            pruneNames = getPruneNames(pruneNames, component.pruneNames, component.pruneConfigFiles),
                            time = false,
                            yield = true,
                        )
                }
            },
            original = versioned,
            pruneContentRegex = pruneContentRegex,
            pruneNames = getPruneNames(pruneNames, versioned.pruneNames, versioned.pruneConfigFiles),
            rootPatterns = latestRootComponent?.let { createPatterns(it) } ?: defaultPatterns,
            yieldRoot = latestRootComponent?.yield ?: false,
        )
    }

    private fun fromSimplePathConditionVersion1(
        versioned: SimplePathConditionVersion1,
        errorHandler: ErrorHandler,
        @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
        defaultMinDepth: Int,
        ignoreNonDirectoriesComponent0: Boolean?,
        pruneContentRegex: Regex?,
        pruneNames: Set<String>?,
    ): PathCondition {
        val components = LinkedList<PathCondition.Component>()
        var patterns = createPatterns(versioned)
        val isTime = versioned.time == true
        val minDepth = versioned.minDepth ?: if (isTime) 0 else defaultMinDepth
        val maxDepth = versioned.maxDepth ?: minDepth
        if (maxDepth < minDepth) {
            errorHandler.onError("maxDepth = $maxDepth < minDepth = $minDepth")
        }
        for (depth in 1..maxDepth) {
            if (components.size == 1) {
                patterns = patterns.copyWithoutWhitelist()
            }
            components += PathCondition.Component(
                enterNames = versioned.enterNames.takeIf { components.isEmpty() } ?: setOf(),
                ignoreNonDirectories = ignoreNonDirectoriesComponent0.takeIf { components.isEmpty() } ?: true,
                index = components.size,
                patterns = patterns,
                pruneNames = setOf(),
                time = false,
                yield = !isTime && depth >= minDepth,
            )
        }
        if (isTime) {
            components += PathCondition.Component(
                enterNames = versioned.enterNames.takeIf { components.isEmpty() } ?: setOf(),
                ignoreNonDirectories = ignoreNonDirectoriesComponent0.takeIf { components.isEmpty() } ?: true,
                index = components.size,
                patterns = patterns,
                pruneNames = setOf(),
                time = true,
                yield = true,
            )
        }
        if (components.size == 1) {
            patterns = patterns.copyWithoutWhitelist()
        }
        return PathCondition(
            components = components,
            finalComponent = null,
            original = versioned,
            pruneContentRegex = pruneContentRegex,
            pruneNames = getPruneNames(pruneNames, versioned.pruneNames, versioned.pruneConfigFiles),
            rootPatterns = patterns,
            yieldRoot = !isTime && minDepth == 0,
        )
    }

    private val defaultPatterns = createPatterns(HasPatterns.Default)

    private fun createPatterns(container: HasPatterns): PathCondition.Patterns =
        PathCondition.Patterns(
            common = createCommonPattern(container),
            directory = createDirectoryPattern(container),
            file = createFilePattern(container),
        )

    private fun createCommonPattern(container: HasCommonPattern): PathCondition.Pattern =
        PathCondition.Pattern(container.nameGlob, container.nameRegex, container.pathRegex, container.whitelist)

    private fun createDirectoryPattern(container: HasDirectoryPattern): PathCondition.Pattern =
        PathCondition.Pattern(container.directoryNameGlob, container.directoryNameRegex, container.directoryPathRegex, container.directoryWhitelist)

    private fun createFilePattern(container: HasFilePattern): PathCondition.Pattern =
        PathCondition.Pattern(container.fileNameGlob, container.fileNameRegex, container.filePathRegex, container.fileWhitelist)

    private fun getPruneNames(pruneNamesA: Set<String>?, pruneNamesB: Set<String>?, pruneConfigFiles: Boolean?): Set<String> {
        val pruneNamesSummand = if (pruneConfigFiles != false) configFileNameSet else setOf()
        return if (pruneNamesA == null && pruneNamesB == null) {
            pruneNamesSummand
        } else {
            val result = pruneNamesSummand.toMutableSet()
            pruneNamesA?.let { result += it }
            pruneNamesB?.let { result += it }
            result
        }
    }

    fun fromVersioned(versioned: NewInodeVersioned): NewInodeVersion1 =
        when (versioned) {
            is NewInodeVersion1 -> versioned
        }

    fun fromVersioned(versioned: RunLastVersioned): RunLastVersion1 =
        when (versioned) {
            is RunLastVersion1 -> versioned
        }

    fun fromVersioned(versioned: TasksVersioned): TasksVersion1 =
        when (versioned) {
            is TasksVersion1 -> versioned
        }

    fun fromVersioned(versioned: FilterVersioned): FilterVersion1 =
        when (versioned) {
            is FilterVersion1 -> versioned
        }

    fun fromVersioned(versioned: ScanModeVersioned): ScanModeVersion1 =
        when (versioned) {
            is ScanModeVersion1 -> versioned
        }

    fun fromVersioned(versioned: InputVersioned): InputVersion1 =
        when (versioned) {
            is InputVersion1 -> versioned
        }

    fun fromVersioned(versioned: OutputVersioned): OutputDirectoryVersion1 =
        when (versioned) {
            is OutputDirectoryVersion1 -> versioned
        }

    fun fromVersioned(versioned: TagVersioned): TagVersion1 =
        when (versioned) {
            is TagVersion1 -> versioned
        }

    fun fromVersioned(versioned: RelationshipDefinitionVersioned): RelationshipDefinitionVersion1 =
        when (versioned) {
            is RelationshipDefinitionVersion1 -> versioned
        }

    fun fromVersioned(versioned: OperationVersioned): OperationVersion1 =
        when (versioned) {
            is OperationVersion1 -> versioned
        }

    fun fromVersioned(versioned: InodeTagVersioned): InodeTagVersion1 =
        when (versioned) {
            is InodeTagVersion1 -> versioned
        }

    fun fromVersioned(versioned: InodeTagPartVersioned): InodeTagPartVersion1 =
        when (versioned) {
            is InodeTagPartVersion1 -> versioned
        }

    fun fromVersioned(versioned: SystemTagsVersioned?): SystemTagsVersion1? =
        when (versioned) {
            is SystemTagsVersion1 -> versioned
            null -> null
        }
}