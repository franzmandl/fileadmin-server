package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.EnumCollection
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.NewInode
import com.franzmandl.fileadmin.dto.config.*
import com.franzmandl.fileadmin.filter.*
import com.franzmandl.fileadmin.vfs.Inode0
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.PathFinder
import com.franzmandl.fileadmin.vfs.SafePath

class ConfigBuilder<T : Inode0>(
    private val ctx: PathFinder.Ctx,
    private val inode: T,
) {
    private val applicationCtx = ctx.request.application
    private var filter: FilterFileSystem? = null
    private var isRunLast: Boolean = false
    private var isTask: Boolean = false
    private var nameCursorPosition: Int? = applicationCtx.config.nameCursorPosition
    private var newInodeTemplate: NewInode = applicationCtx.config.newInodeTemplate
    private val ancestors = Inode0.getAncestorMap(inode)
    private val registerTagHelper = RegisterTagHelper(ctx)

    class ConfigFile(
        val versioned: ConfigRoot,
        val directory: SafePath,
        val file: Inode0,
        val importLevel: Int,
    )

    fun build(configFiles: Iterable<ConfigFile>, filterResult: FilterResult?): Inode1<T> {
        for (configFile in configFiles) {
            onRoot(configFile.versioned, configFile.directory, configFile.file, configFile.importLevel)
        }
        return Inode1(
            Inode1.Config(
                errors = ctx.errors,
                filter = filter,
                filterResult = filterResult,
                isRunLast = isRunLast,
                isTask = isTask,
                nameCursorPosition = nameCursorPosition,
                newInodeTemplate = newInodeTemplate,
                samePaths = ctx.samePaths,
                stepchildren = ctx.request.stepchildren[inode.path] ?: setOf()
            ), inode
        )
    }

    private fun onFilter(versioned: FilterVersioned, configDirectory: SafePath, configFile: Inode0, importLevel: Int) {
        val latest = applicationCtx.mapper.fromVersioned(versioned)
        if (ctx.filterConfigFiles.add(configFile.path)) {
            for (versionedRelationship in latest.relationships ?: listOf()) {
                val definition = applicationCtx.mapper.fromVersioned(versionedRelationship ?: continue)
                if (ctx.filterRelationshipDefinitions.put(definition.name, ConfigRelationshipDefinition.create(configFile, applicationCtx.mapper, definition)) != null) {
                    ctx.onError("""${configFile.path.absoluteString}: Relationship "${definition.name}" already defined.""")
                }
            }
            for (versionedTag in latest.tags ?: listOf()) {
                ctx.filterTags += ConfigTag.create(configFile, applicationCtx.mapper, versionedTag) ?: continue
            }
            for (versionedOperation in latest.operations ?: listOf()) {
                ctx.filterOperations += ConfigOperation.create(configFile, applicationCtx.mapper, versionedOperation) ?: continue
            }
        }
        if (importLevel == 0 && latest.output != null && latest.enabled != false) {
            val latestOutput = applicationCtx.mapper.fromVersioned(latest.output)
            val outputDirectory = configDirectory.forceResolve(latestOutput.path)
            var canScanItems = false
            val system = ctx.request.application.cacheFilterFileSystem(outputDirectory, ctx.lastModified) {
                canScanItems = it
                createFilterCtx(configDirectory, configFile, latest, latestOutput, outputDirectory)
            }
            ctx.request.stepchildren.computeIfAbsent(outputDirectory.forceParent()) { mutableSetOf() } += outputDirectory
            ctx.request.fileSystems[outputDirectory] = system
            filter = system
            if (canScanItems) {
                system.ctx.scanItems(ctx.request, CommandId.FirstScanItems, ctx)
                system.ctx.registry.check(ctx)
            }
        }
    }

    private fun createFilterCtx(
        configDirectory: SafePath,
        configFile: Inode0,
        latest: FilterVersion1,
        latestOutput: OutputDirectoryVersion1,
        outputDirectory: SafePath
    ): FilterCtx {
        val latestSystemTags = applicationCtx.mapper.fromVersioned(latest.systemTags) ?: SystemTagsVersion1()
        val registryPhase1 = TagRegistry.Phase1(
            coerceSuggestMinimumLength = applicationCtx.filter::coerceSuggestMinimumLength, ctx,
            SystemTags.map(latestSystemTags, applicationCtx.filter.systemTags) { name, otherwise ->
                validateTagName(configFile.path, name?.let { FilterFileSystem.trimPrefix(it, ctx) }, otherwise)
            },
        )
        registerTagHelper.registerTags(registryPhase1, ctx.filterTags)
        val registryPhase2 = registryPhase1.startPhase2()
        for (operation in ctx.filterOperations) {
            OperationHelper(ctx, operation, registerTagHelper, registryPhase1).handleOperation()
        }
        val inputs = mutableListOf<Input>()
        val lostAndFound = getOrCreateLostAndFound(registryPhase2, latest.lostAndFound, registryPhase2.systemTags.lostAndFound)
        for (versionedInput in latest.input ?: listOf()) {
            val latestInput = applicationCtx.mapper.fromVersioned(versionedInput ?: continue).takeIf { it.enabled != false } ?: continue
            val oldSize = inputs.size
            val inputLostAndFound = getOrCreateLostAndFound(registryPhase2, latestInput.lostAndFound, lostAndFound)
            if (latestInput.path != null) {
                inputs += createInput(configFile, latestInput, configDirectory.forceResolve(latestInput.path), registryPhase1, inputLostAndFound)
            }
            for (path in latestInput.paths ?: listOf()) {
                inputs += createInput(configFile, latestInput, configDirectory.forceResolve(path), registryPhase1, inputLostAndFound)
            }
            if (oldSize == inputs.size) {
                ctx.onError("${configFile.path.absoluteString}: An enabled input does not have any paths.")
            }
            if (latestInput.name == null && latestInput.automaticInputTag != false) {
                val firstName = inputs.getOrNull(oldSize)?.path?.name
                for (index in (oldSize + 1)..inputs.lastIndex) {
                    if (inputs[index].path.name != firstName) {
                        ctx.onError("${configFile.path.absoluteString}: All input paths do not result in the same name. Specify a name explicitly.")
                        break
                    }
                }
            }
        }
        val scanModes = EnumCollection(CommandId::class) { commandId ->
            ScanMode.create(commandId, latest.scanModes?.get(commandId)?.let { applicationCtx.mapper.fromVersioned(it) })
        }
        return FilterCtx(
            registryPhase2, inputs, outputDirectory,
            autoIntersect = latestOutput.autoIntersect ?: true,
            hierarchicalTags = latestOutput.hierarchicalTags ?: true,
            rootTagsMinPriority = latestOutput.rootTagsMinPriority,
            scanModes = scanModes,
        )
    }

    private fun getOrCreateLostAndFound(registry: TagRegistry, name: String?, default: Tag.Mutable?): Tag.Mutable? =
        if (name === ConfigConstant.undefinedString) {
            default
        } else if (name == null) {
            null
        } else {
            registry.getOrCreateTag(FilterFileSystem.trimPrefix(name, ctx), Tag.Parameter.system0, ctx)
        }

    private fun createInput(configFile: Inode0, latest: InputVersion1, path: SafePath, registry: TagRegistry, lostAndFound: Tag?): Input {
        val automaticTags = mutableSetOf<Tag.Mutable>()
        for (aliases in latest.automaticTags ?: listOf()) {
            automaticTags += registerTagHelper.getOrCreateTags(
                registry,
                TagNameHelper.getSequenceOfAliases(ctx, configFile.path, aliases, TagNameHelper.nameResolverWithValidation),
                configFile, Tag.Parameter.standard, latest = null
            )
        }
        automaticTags += if (latest.automaticInputTag != false) {
            val inputTagName = ctx.request.application.filter.inputTagPrefix + (latest.name ?: path.name) + ctx.request.application.filter.inputTagSuffix
            if (FilterFileSystem.isValidName(inputTagName)) {
                LinkTagHelper.linkParent(configFile.path, registry.getOrCreateTag(inputTagName, Tag.Parameter.cannotRename, ctx), registry.systemTags.input, false, ctx)
            } else {
                ctx.onError("""${configFile.path.absoluteString}: Input "$inputTagName" is no valid name for a tag.""")
                registry.systemTags.input
            }
        } else {
            registry.systemTags.input
        }
        val errorHandler = ErrorHandler.concatenate(ctx, configFile.path.absoluteString)
        val condition = applicationCtx.mapper.fromVersioned(
            latest.condition ?: SimplePathConditionVersion1.default, errorHandler,
            ignoreNonDirectoriesComponent0 = false,
            pruneContentRegex = registry.pruneContentRegex,
            pruneNames = latest.pruneNames,
        )
        return Input(
            automaticTags = automaticTags,
            condition = condition,
            contentCondition = applicationCtx.mapper.fromVersioned(
                latest.contentCondition ?: SimplePathConditionVersion1.default, errorHandler,
                defaultMinDepth = 0,
                pruneContentRegex = registry.pruneContentRegex,
                pruneNames = latest.pruneNames,
            ),
            inodeTag = InodeTag.create(
                applicationCtx.mapper,
                latest.inodeTag,
                condition.components.size + if (condition.finalComponent != null) 1 else 0,
                registry.systemTags.input, // Provided as non-null dummy tag. Makes no difference since every item has this tag.
                errorHandler,
            ),
            lostAndFound = lostAndFound,
            path = path,
            scanNameForTags = latest.scanNameForTags ?: true,
            scanParentPathForTags = latest.scanParentPathForTags ?: true,
        )
    }

    private fun validateTagName(configPath: SafePath, name: String?, otherwise: String): String =
        if (name == null) {
            otherwise
        } else if (FilterFileSystem.isValidName(name)) {
            name
        } else {
            ctx.onError("""${configPath.absoluteString}: "$name" is no valid name for a tag.""")
            otherwise
        }

    private fun onNewInodeTemplate(versioned: NewInodeVersioned, configDirectory: SafePath, valueIfNull: Boolean, errorHandler: ErrorHandler) {
        val latest = applicationCtx.mapper.fromVersioned(versioned)
        if (evaluateCondition(latest.condition, valueIfNull, configDirectory, errorHandler)) {
            newInodeTemplate = NewInode(
                isFile = latest.isFile ?: newInodeTemplate.isFile,
                name = latest.name ?: newInodeTemplate.name,
            )
        }
    }

    private fun onRoot(versioned: ConfigRoot, configDirectory: SafePath, configFile: Inode0, importLevel: Int) {
        val errorHandler = ErrorHandler.concatenate(ctx, configFile.path.absoluteString)
        when (versioned) {
            is ConfigValue -> onValue(versioned, configDirectory, errorHandler)
            is DirectoryVersioned -> {
                val latest = applicationCtx.mapper.fromVersioned(versioned)
                if (latest.filter != null) {
                    onFilter(latest.filter, configDirectory, configFile, importLevel)
                }
                if (latest.tags != null) {
                    ctx.filterTags += latest.tags.mapNotNull { ConfigTag.create(configFile, applicationCtx.mapper, it) }
                }
                for (value in latest.values ?: listOf()) {
                    if (value == null) {
                        continue
                    }
                    onValue(value, configDirectory, errorHandler)
                }
            }

            is FilterVersioned -> onFilter(versioned, configDirectory, configFile, importLevel)
        }
    }

    private fun onRunLast(versioned: RunLastVersioned, configDirectory: SafePath, valueIfNull: Boolean, errorHandler: ErrorHandler) {
        val latest = applicationCtx.mapper.fromVersioned(versioned)
        if (evaluateCondition(latest.condition, valueIfNull, configDirectory, errorHandler)) {
            isRunLast = latest.enabled != false
        }
    }

    private fun onTasks(versioned: TasksVersioned, configDirectory: SafePath, valueIfNull: Boolean, errorHandler: ErrorHandler) {
        val latest = applicationCtx.mapper.fromVersioned(versioned)
        if (evaluateCondition(latest.condition, valueIfNull, configDirectory, errorHandler)) {
            isTask = latest.enabled != false
        }
    }

    private fun onValue(versioned: ConfigValue, configDirectory: SafePath, errorHandler: ErrorHandler) {
        when (versioned) {
            is DirectoryValueVersioned -> {
                val latest = applicationCtx.mapper.fromVersioned(versioned)
                if (evaluateCondition(latest.condition, inode.path == configDirectory, configDirectory, errorHandler)) {
                    nameCursorPosition = latest.nameCursorPosition ?: nameCursorPosition
                    latest.newInodeTemplate?.let { onNewInodeTemplate(it, configDirectory, true, errorHandler) }
                    latest.runLast?.let { onRunLast(it, configDirectory, true, errorHandler) }
                    latest.tasks?.let { onTasks(it, configDirectory, true, errorHandler) }
                }
            }

            is NewInodeVersioned -> onNewInodeTemplate(versioned, configDirectory, inode.path == configDirectory, errorHandler)
            is RunLastVersioned -> onRunLast(versioned, configDirectory, inode.path == configDirectory, errorHandler)
            is TasksVersion1 -> onTasks(versioned, configDirectory, inode.path == configDirectory, errorHandler)
        }
    }

    private fun evaluateCondition(versioned: PathConditionVersioned?, valueIfNull: Boolean, configDirectory: SafePath, errorHandler: ErrorHandler): Boolean {
        if (versioned == null) {
            return valueIfNull
        }
        if (!inode.path.startsWith(configDirectory)) {
            // Happens when a config file imports a config file having a different name than applicationCtx.configFileName.
            // Returning false leads to a defined behavior in such case.
            return false
        }
        return applicationCtx.mapper.fromVersioned(versioned, errorHandler, defaultMinDepth = 0)
            .evaluate(configDirectory, ancestors, inode.path.sliceParts(configDirectory), ctx)
    }
}