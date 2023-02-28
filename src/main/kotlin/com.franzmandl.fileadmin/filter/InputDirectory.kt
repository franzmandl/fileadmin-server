package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.model.config.ConditionVersion1
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.SafePath
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.FileTime
import java.util.*

class InputDirectory(
    private val ctx: RequestCtx,
    private val automaticInputTags: Set<Tag>,
    condition: ConditionVersion1,
    private val contentCondition: ConditionVersion1,
    private val path: SafePath,
    private val pruneNames: Set<String>,
) {
    private val minDepth = condition.minDepth ?: 0
    private val maxDepth = condition.maxDepth ?: minDepth
    private var previousLastModified: FileTime? = null
    private var items = listOf<Item>()

    fun getItems(registry: TagRegistry, force: Boolean, onError: (String) -> Unit): List<Item> {
        val directory = ctx.getInode(path)
        if (!directory.contentPermission.canDirectoryGet) {
            onError("Input '$path' not available.")
            previousLastModified = null
            items = listOf()
        } else {
            val currentLastModified = getCurrentLastModified(directory, onError)
            if (force || currentLastModified == null || currentLastModified != previousLastModified) {
                previousLastModified = currentLastModified
                items = computeItems(directory, registry, onError)
            }
        }
        return items
    }

    private fun getCurrentLastModified(directory: Inode, onError: (String) -> Unit): FileTime? {
        var lastModified: FileTime? = null
        for (inode in CommonUtil.getSequenceOfDescendants(ctx.createPathFinderCtx(), directory, 0, maxDepth, pruneNames, onError)) {
            val currentLastModified = inode.lastModified
            if (currentLastModified == null) {
                return null
            } else {
                lastModified = lastModified?.coerceAtLeast(currentLastModified) ?: currentLastModified
            }
        }
        return lastModified
    }

    private fun computeItems(directory: Inode, registry: TagRegistry, onError: (String) -> Unit): List<Item> {
        val items = LinkedList<Item>()
        for (inode in CommonUtil.getSequenceOfDescendants(ctx.createPathFinderCtx(), directory, minDepth, maxDepth, pruneNames, onError)) {
            val automaticTags = ItemTags.Mutable()
            automaticInputTags.forEach { automaticTags.addTag(it, false) }
            val content = try {
                val commonCondition = evaluateContentCondition(
                    inode, contentCondition.compiledNameGlobs, contentCondition.compiledNameRegex, contentCondition.compiledPathRegex
                )
                when {
                    inode.contentOperation.canFileGet && (commonCondition || evaluateContentCondition(
                        inode, contentCondition.compiledFileNameGlobs, contentCondition.compiledFileNameRegex, contentCondition.compiledFilePathRegex
                    )) -> inode.text

                    inode.contentOperation.canDirectoryGet && (commonCondition || evaluateContentCondition(
                        inode, contentCondition.compiledDirectoryNameGlobs, contentCondition.compiledDirectoryNameRegex, contentCondition.compiledDirectoryPathRegex
                    )) -> Inode.getChildrenAsText(inode)

                    else -> null
                }
            } catch (e: HttpException) {
                onError(e.message)
                null
            }
            items.add(Item.create(registry, inode, automaticTags, path.absoluteString.length, content))
        }
        return items
    }

    private fun evaluateContentCondition(inode: Inode, nameGlobs: List<PathMatcher>?, nameRegex: Regex?, pathRegex: Regex?): Boolean =
        nameGlobs?.let { CommonUtil.evaluateGlobs(it, Path.of(inode.path.name)) } ?: false
                || nameRegex?.containsMatchIn(inode.path.name) ?: false
                || pathRegex?.containsMatchIn(inode.path.absoluteString) ?: false
}