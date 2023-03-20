package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.filter.*
import com.franzmandl.fileadmin.model.NewInode
import com.franzmandl.fileadmin.model.config.*
import com.franzmandl.fileadmin.resource.RequestCtx
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.*

class PathFinder(
    val ctx: Ctx,
    val destination: SafePath,
) : Inode.Config {
    private val logger: Logger = LoggerFactory.getLogger(PathFinder::class.java)
    private val default = ctx.request.application.default
    override val errors: List<String> = ctx.errors
    override var filter: FilterFileSystem? = null
        private set
    override var filterHighlightTags: Set<Tag>? = null
    override var isRunLast: Boolean = false
        private set
    override var isTask: Boolean = false
        private set
    override var nameCursorPosition: Int? = default.nameCursorPosition
        private set
    override var newInodeTemplate: NewInode = default.newInodeTemplate
        private set
    var parent: OptionalValue<InodeWithoutConfig> = Inode.parentOfRoot

    class ConfigTag(
        val children: List<ConfigTag>,
        val configFile: Inode,
        val latest: TagVersion1,
    ) {
        constructor(configFile: Inode, latest: TagVersion1) : this(
            latest.children?.mapNotNull { it?.let { ConfigTag(configFile, it.toLatestVersion()) } } ?: listOf(),
            configFile, latest
        )
    }

    class Ctx(
        val configFiles: MutableSet<SafePath>,
        val request: RequestCtx,
        val errors: MutableList<String>,
    ) {
        val filterTags = LinkedList<ConfigTag>()
        var lastModified: FileTime? = null
        var lastModifiedIsUnknown = false

        fun createPathFinder(destination: SafePath): PathFinder =
            PathFinder(this, destination)

        fun copy(): Ctx =
            Ctx(configFiles, request, errors)
    }

    fun find(system: RootFileSystem): Inode =
        system.getInode(this)

    fun find(): Inode =
        find(ctx.request.application.paths.jail)

    fun visitInode(configFile: Inode, importLevel: Int = 0) {
        if (!ctx.configFiles.add(configFile.path)) {
            return
        }
        val versioned = try {
            ctx.request.cacheConfigFile(configFile)
        } catch (e: IllegalArgumentException) {
            ctx.errors.add("${configFile.path}: JSON decoding error: ${e.message}")
            return
        }
        val configDirectory = configFile.path.parent
        if (configDirectory == null) {
            ctx.errors.add("${configFile.path}: Has no parent.")
            return
        }
        if (!ctx.lastModifiedIsUnknown) {
            val configFileLastModified = configFile.lastModified
            if (configFileLastModified == null) {
                ctx.lastModifiedIsUnknown = true
                ctx.lastModified = null
            } else {
                ctx.lastModified = ctx.lastModified?.coerceAtLeast(configFileLastModified) ?: configFileLastModified
            }
        }
        try {
            val imports = versioned.imports
            if (!imports.isNullOrEmpty()) {
                val nextImportLevel = importLevel + 1
                if (nextImportLevel > ctx.request.application.configMaxImportLevel) {
                    ctx.errors.add("${configFile.path}: Max import level exceeded while importing \"${imports.joinToString("\", \"")}\"")
                }
                for (import in imports) {
                    if (import == null) {
                        continue
                    }
                    for (subPath in CommonUtil.getPartsList(configDirectory.forceResolve(import).parts, true)) {
                        visitImport(SafePath(subPath), nextImportLevel)
                    }
                }
            }
            onRoot(versioned, configDirectory, configFile, importLevel)
        } catch (e: Exception) {
            ctx.request.application.incrementLogId().let {
                logger.warn(it, e)
                ctx.errors.add("${configFile.path} (see log $it): ${HttpException.getMessage(e)}")
            }
        }
    }

    private fun visitImport(path: SafePath, importLevel: Int) {
        val inode = ctx.createPathFinder(path).find().let {
            when {
                importLevel > 0 && it.isFile -> it
                else -> ctx.createPathFinder(it.path.resolve(ctx.request.application.configFileName)).find()
            }
        }
        if (inode.contentPermission.canFileGet) {
            visitInode(inode, importLevel)
        }
    }

    private fun onFilter(versioned: FilterVersioned, configDirectory: SafePath, configFile: Inode, importLevel: Int) {
        val latest = versioned.toLatestVersion()
        latest.tags?.mapNotNullTo(ctx.filterTags) { it?.let { ConfigTag(configFile, it.toLatestVersion()) } }
        if (importLevel == 0 && latest.output != null) {
            val latestOutput = latest.output.toLatestVersion()
            val outputDirectory = configDirectory.forceResolve(latestOutput.path)
            val system = ctx.request.application.cacheFilterFileSystem(outputDirectory, ctx.lastModified) {
                val registry = TagRegistry(
                    tagDirectoryName = validateTagName(configFile.path, latestOutput.tagDirectory, default.tagDirectory),
                    tagFileName = validateTagName(configFile.path, latestOutput.tagFile, default.tagFile),
                    tagInputName = validateTagName(configFile.path, latestOutput.tagInput, default.tagInput),
                    tagLostAndFoundName = validateTagName(configFile.path, latestOutput.tagLostAndFound, default.tagLostAndFound),
                    tagUnknownName = validateTagName(configFile.path, latestOutput.tagUnknown, default.tagUnknown),
                )
                registerFilterTags(registry, ctx.filterTags, listOf(), true)
                val inputs = latest.input?.mapNotNull { versionedInput ->
                    val latestInput = versionedInput?.toLatestVersion()
                    if (latestInput == null || latestInput.enable == false) {
                        null
                    } else {
                        val path = configDirectory.forceResolve(latestInput.path)
                        val automaticTags = mutableSetOf<Tag.Mutable>()
                        if (latestInput.automaticTags != null) {
                            for (aliases in latestInput.automaticTags) {
                                getOrCreateTagsTo(automaticTags, registry, aliases, configFile, false, null)
                            }
                        }
                        if (latestInput.automaticInputTag != false) {
                            if (FilterFileSystem.isValidName(path.name)) {
                                automaticTags.add(addParentTag(configFile.path, registry.getOrCreateTag(path.name, null), registry.tagInput))
                            } else {
                                ctx.errors.add("${configFile.path}: Input '${path.name}' is no valid name for a tag.")
                            }
                        } else {
                            automaticTags.add(registry.tagInput)
                        }
                        Input(
                            automaticTags = automaticTags,
                            condition = latestInput.condition?.toLatestVersion() ?: ConditionVersion1.default,
                            contentCondition = latestInput.contentCondition?.toLatestVersion() ?: ConditionVersion1.default,
                            path = path,
                            pruneNames = latestInput.pruneNames ?: setOf(),
                        )
                    }
                } ?: listOf()
                FilterCtx(
                    registry, inputs, outputDirectory,
                    hierarchicalTags = latestOutput.hierarchicalTags ?: true,
                )
            }
            ctx.request.stepchildren.computeIfAbsent(outputDirectory.forceParent()) { mutableSetOf() }.add(outputDirectory)
            ctx.request.fileSystems[outputDirectory] = system
            filter = system
        }
    }

    private fun validateTagName(configPath: SafePath, name: String?, otherwise: String): String =
        if (name == null) {
            otherwise
        } else if (FilterFileSystem.isValidName(name)) {
            name
        } else {
            ctx.errors.add("$configPath: '$name' is no valid name for a tag.")
            otherwise
        }

    private fun registerFilterTags(registry: TagRegistry, configTags: Iterable<ConfigTag>, implicitParents: Iterable<Tag.Mutable>, isRoot: Boolean) {
        for (configTag in configTags) {
            val tags = getOrCreateTags(registry, configTag.configFile, isRoot, configTag.latest)
            val parents = if (configTag.latest.parents != null) {
                val explicitParents = mutableListOf<Tag.Mutable>()
                for (aliases in configTag.latest.parents) {
                    getOrCreateTagsTo(explicitParents, registry, aliases, configTag.configFile, false, null)
                }
                explicitParents
            } else {
                implicitParents
            }
            val spread = configTag.latest.spread == true
            for (tag in tags) {
                for (parent in parents) {
                    if (spread) {
                        parent.addChildrenOf(tag)
                    }
                    addParentTag(configTag.configFile.path, tag, parent)
                }
            }
            registerFilterTags(registry, configTag.children, tags, false)
        }
    }

    private fun getOrCreateTags(registry: TagRegistry, configFile: Inode, isRoot: Boolean, latest: TagVersion1): List<Tag.Mutable> =
        getOrCreateTagsTo(mutableListOf(), registry, latest.name, configFile, isRoot, latest)

    private fun <C : MutableCollection<Tag.Mutable>> getOrCreateTagsTo(destination: C, registry: TagRegistry, aliases: String, configFile: Inode, isRoot: Boolean, latest: TagVersion1?): C {
        var twin: Tag.Mutable? = null
        return aliases.split(FilterFileSystem.tagPrefixString).mapIndexedNotNullTo(destination) { index, name ->
            if (index == 0) {
                if (name != "") {
                    ctx.errors.add("${configFile.path}: Missing ${FilterFileSystem.tagPrefix} before tag name in '$aliases'.")
                }
                null
            } else if (FilterFileSystem.isValidName(name)) {
                val tag = registry.getOrCreateTag(name).addConfigFile(configFile)
                twin?.let { setTwinTag(tag, it) }
                twin = tag
                tag.parameter.isRoot = tag.parameter.isRoot || isRoot
                if (latest != null) {
                    tag.parameter.setCanRename(latest.canRename)
                    tag.parameter.setPriority(latest.priority)
                }
                tag
            } else {
                ctx.errors.add("${configFile.path}: '$name' is no valid name for a tag.")
                null
            }
        }
    }

    private fun setTwinTag(tag: Tag.Mutable, twin: Tag.Mutable) {
        if (tag != twin && !twin.hasTwin(tag)) {
            twin.setTwin(tag)
        }
    }

    private fun addParentTag(configPath: SafePath, tag: Tag.Mutable, parent: Tag.Mutable): Tag.Mutable {
        if (tag != parent && !tag.hasDescendant(parent)) {
            tag.addParent(parent)
        } else {
            ctx.errors.add("$configPath: Parent loop detected between ${tag.friendlyName} and ${parent.friendlyName}.")
        }
        return tag
    }

    private fun onNewInodeTemplate(versioned: NewInodeVersioned, configDirectory: SafePath, valueIfNull: Boolean) {
        val latest = versioned.toLatestVersion()
        if (evaluateCondition(latest.condition, configDirectory) ?: valueIfNull) {
            newInodeTemplate = NewInode(
                latest.isFile ?: newInodeTemplate.isFile,
                latest.name ?: newInodeTemplate.name,
            )
        }
    }

    private fun onRoot(versioned: ConfigRoot, configDirectory: SafePath, configFile: Inode, importLevel: Int) {
        when (versioned) {
            is ConfigValue -> onValue(versioned, configDirectory)
            is DirectoryVersioned -> {
                val latest = versioned.toLatestVersion()
                if (latest.filter != null) {
                    onFilter(latest.filter, configDirectory, configFile, importLevel)
                }
                if (latest.tags != null) {
                    ctx.filterTags.addAll(latest.tags.mapNotNull { it?.let { ConfigTag(configFile, it.toLatestVersion()) } })
                }
                for (value in latest.values ?: listOf()) {
                    if (value == null) {
                        continue
                    }
                    onValue(value, configDirectory)
                }
            }

            is FilterVersioned -> onFilter(versioned, configDirectory, configFile, importLevel)
        }
    }

    private fun onRunLast(versioned: RunLastVersioned, configDirectory: SafePath, valueIfNull: Boolean) {
        val latest = versioned.toLatestVersion()
        if (evaluateCondition(latest.condition, configDirectory) ?: valueIfNull) {
            isRunLast = latest.enable != false
        }
    }

    private fun onTasks(versioned: TasksVersioned, configDirectory: SafePath, valueIfNull: Boolean) {
        val latest = versioned.toLatestVersion()
        if (evaluateCondition(latest.condition, configDirectory) ?: valueIfNull) {
            isTask = latest.enable != false
        }
    }

    private fun onValue(versioned: ConfigValue, configDirectory: SafePath) {
        when (versioned) {
            is DirectoryValueVersioned -> {
                val latest = versioned.toLatestVersion()
                if (evaluateCondition(latest.condition, configDirectory) ?: (destination == configDirectory)) {
                    nameCursorPosition = latest.nameCursorPosition ?: nameCursorPosition
                    latest.newInodeTemplate?.let { onNewInodeTemplate(it, configDirectory, true) }
                    latest.runLast?.let { onRunLast(it, configDirectory, true) }
                    latest.tasks?.let { onTasks(it, configDirectory, true) }
                }
            }

            is NewInodeVersioned -> onNewInodeTemplate(versioned, configDirectory, destination == configDirectory)
            is RunLastVersioned -> onRunLast(versioned, configDirectory, destination == configDirectory)
            is TasksVersion1 -> onTasks(versioned, configDirectory, destination == configDirectory)
        }
    }

    private fun evaluateCondition(versioned: ConditionVersioned?, configDirectory: SafePath): Boolean? {
        val latest = versioned?.toLatestVersion() ?: return null
        var result = true
        var startsWith: Boolean? = null
        if (latest.commonCondition.nameGlobs != null) {
            startsWith = if (startsWith ?: destination.startsWith(configDirectory)) {
                result = result && CommonUtil.evaluateGlobs(
                    latest.commonCondition.nameGlobs,
                    Path.of(destination.relativeString.substring((configDirectory.relativeString.length + if (destination.relativeString.length > configDirectory.relativeString.length) 1 else 0))),
                )
                true
            } else false
        }
        if (latest.maxDepth != null) {
            startsWith = if (startsWith ?: destination.startsWith(configDirectory)) {
                result = result && (destination.parts.size - configDirectory.parts.size <= latest.maxDepth)
                true
            } else false
        }
        if (latest.minDepth != null) {
            @Suppress("UNUSED_VALUE")
            startsWith = if (startsWith ?: destination.startsWith(configDirectory)) {
                result = result && (destination.parts.size - configDirectory.parts.size >= latest.minDepth)
                true
            } else false
        }
        if (latest.commonCondition.nameRegex != null) {
            result = result && latest.commonCondition.nameRegex.matches(destination.name)
        }
        if (latest.commonCondition.pathRegex != null) {
            result = result && latest.commonCondition.pathRegex.matches(destination.absoluteString)
        }
        return result
    }
}