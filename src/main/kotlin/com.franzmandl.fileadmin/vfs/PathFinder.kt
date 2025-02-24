package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.filter.FilterResult
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.config.*
import org.slf4j.LoggerFactory
import java.nio.file.attribute.FileTime
import java.util.*

class PathFinder(
    val ctx: Ctx,
    val destination: SafePath,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    var parent: OptionalValue<Inode0> = Inode0.parentOfRoot
    private val configFiles = LinkedList<ConfigBuilder.ConfigFile>()

    fun <T : Inode0> build(inode: T, filterResult: FilterResult?): Inode1<T> =
        ConfigBuilder(ctx, inode).build(configFiles, filterResult)

    class Ctx private constructor(
        val configFiles: MutableSet<SafePath>,
        val errors: MutableList<String>,
        val filterIsScanning: Boolean,
        val request: RequestCtx,
        val samePaths: MutableSet<SafePath>,
    ) : ErrorHandler {
        constructor(filterIsScanning: Boolean, request: RequestCtx) : this(mutableSetOf(), LinkedList(), filterIsScanning, request, mutableSetOf())

        /** Contains files whose content has already been processed for filter stuff. */
        val filterConfigFiles = mutableSetOf<SafePath>()
        val filterOperations = LinkedList<ConfigOperation>()
        val filterRelationshipDefinitions = mutableMapOf<String, ConfigRelationshipDefinition>()
        val filterTags = LinkedList<ConfigTag>()
        var lastModified: FileTime? = null
        var lastModifiedIsUnknown = false

        fun createPathFinder(destination: SafePath): PathFinder =
            PathFinder(this, destination)

        fun copy(samePath: SafePath): Ctx =
            Ctx(configFiles, errors, filterIsScanning, request, samePaths.apply { add(samePath) })

        override fun onError(message: String): Nothing? {
            errors += message
            return null
        }
    }

    fun find(system: RootFileSystem): Inode1<*> =
        system.getInode(this)

    fun find(): Inode1<*> =
        find(ctx.request.application.jail.fileSystem)

    fun visitConfigFile(configFile: Inode0, importLevel: Int = 0) {
        if (!ctx.configFiles.add(configFile.path)) {
            return
        }
        val versioned = try {
            ctx.request.cacheConfigFile(configFile)
        } catch (e: IllegalArgumentException) {
            val details = ConfigFileErrorDetails.create(e, configFile::text)
            ctx.onError("${configFile.path}: JSON decoding error${details ?: ""}: ${e.message}")
            return
        }
        val configDirectory = configFile.path.parent
        if (configDirectory == null) {
            ctx.onError("${configFile.path}: Has no parent.")
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
                if (nextImportLevel > ctx.request.application.config.maxImportLevel) {
                    ctx.onError("""${configFile.path}: Max import level exceeded while importing "${imports.joinToString("""", """")}".""")
                } else {
                    for (import in imports) {
                        if (import == null) {
                            continue
                        }
                        val parts = configDirectory.forceResolve(import).parts
                        for (subPath in CommonUtil.getSequenceOfParts(parts, true)) {
                            visitImport(SafePath(subPath), nextImportLevel, subPath.size == parts.size)
                        }
                    }
                }
            }
            configFiles += ConfigBuilder.ConfigFile(versioned, configDirectory, configFile, importLevel)
        } catch (e: Exception) {
            ctx.request.application.incrementLogId().let {
                logger.warn(it, e)
                ctx.onError("${configFile.path} (see log $it): ${HttpException.getMessage(e)}")
            }
        }
    }

    private fun visitImport(path: SafePath, importLevel: Int, errorEnabled: Boolean) {
        val inode = ctx.createPathFinder(path).find().let {
            when {
                importLevel > 0 && it.inode0.isFile -> it
                else -> ctx.createPathFinder(it.inode0.path.resolve(ctx.request.application.config.fileName)).find()
            }
        }
        if (inode.inode0.contentPermission.canFileGet) {
            visitConfigFile(inode.inode0, importLevel)
        } else if (errorEnabled) {
            ctx.onError("$path: Cannot be imported.")
            ctx.lastModifiedIsUnknown = true
            ctx.lastModified = null
        }
    }
}