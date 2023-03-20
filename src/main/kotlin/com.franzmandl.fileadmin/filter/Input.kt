package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.model.config.ConditionVersion1
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.SafePath
import java.nio.file.attribute.FileTime
import java.util.*

class Input(
    val automaticTags: Set<Tag>,
    private val condition: ConditionVersion1,
    val contentCondition: ConditionVersion1,
    private val path: SafePath,
    val pruneNames: Set<String>,
) {
    private var previousLastModified: FileTime? = null
    private var items = listOf<Item>()

    fun getItems(ctx: RequestCtx, registry: TagRegistry, force: Boolean, onError: (String) -> Unit): List<Item> {
        val directory = ctx.getInode(path)
        if (!directory.contentPermission.canDirectoryGet) {
            onError("Input '$path' not available.")
            previousLastModified = null
            items = listOf()
        } else {
            val currentLastModified = getCurrentLastModified(ctx, directory, onError)
            if (force || currentLastModified == null || currentLastModified != previousLastModified) {
                previousLastModified = currentLastModified
                items = computeItems(ctx, directory, registry, onError)
            }
        }
        return items
    }

    private fun getCurrentLastModified(ctx: RequestCtx, directory: Inode, onError: (String) -> Unit): FileTime? {
        var lastModified: FileTime? = null
        for (inode in CommonUtil.getSequenceOfDescendants(ctx.createPathFinderCtx(), directory, 0, condition.defaultMaxDepth + contentCondition.defaultMaxDepth, pruneNames, onError)) {
            val currentLastModified = inode.lastModified
            if (currentLastModified == null) {
                return null
            } else {
                lastModified = lastModified?.coerceAtLeast(currentLastModified) ?: currentLastModified
            }
        }
        return lastModified
    }

    private fun computeItems(ctx: RequestCtx, directory: Inode, registry: TagRegistry, onError: (String) -> Unit): List<Item> {
        val items = LinkedList<Item>()
        for (inode in CommonUtil.getSequenceOfDescendants(ctx.createPathFinderCtx(), directory, condition.defaultMinDepth, condition.defaultMaxDepth, pruneNames, onError)) {
            val contentBuilder = StringBuilder()
            ItemContent.visit(ctx.createPathFinderCtx(), inode, ContentVisitor(contentCondition, onError, pruneNames, contentBuilder))
            items.add(Item.create(registry, inode, this, path.absoluteString.length, contentBuilder))
        }
        return items
    }

    private class ContentVisitor(
        override val condition: ConditionVersion1,
        override val onError: (String) -> Unit,
        override val pruneNames: Set<String>,
        private val contentBuilder: StringBuilder,
    ) : ItemContent.Visitor {
        override fun onDirectory(inode: Inode): Boolean {
            Inode.getChildrenAsTextTo(contentBuilder, inode)
            return true
        }

        override fun onFile(inode: Inode): Boolean {
            contentBuilder.appendLine(inode.text)
            return true
        }

        override fun onName(inode: Inode): Boolean {
            contentBuilder.appendLine(inode.path.name)
            return true
        }
    }
}