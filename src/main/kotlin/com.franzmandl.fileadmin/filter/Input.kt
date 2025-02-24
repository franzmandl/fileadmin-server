package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.*
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class Input(
    val automaticTags: Set<Tag>,
    val condition: PathCondition,
    val contentCondition: PathCondition,
    val inodeTag: InodeTag?,
    val lostAndFound: Tag?,
    val path: SafePath,
    val scanNameForTags: Boolean,
    val scanParentPathForTags: Boolean,
) {
    private var previousLastModified: FileTime? = null
    private val items = LinkedList<Item>()
    private val paths = mutableMapOf<SafePath, Item>()
    private val timeDirectories = mutableSetOf<SafePath>()
    private val lock = ReentrantReadWriteLock()

    fun getItemsLocked(ctx: RequestCtx, registry: TagRegistry.Phase2, scanMode: ScanMode, errorHandler: ErrorHandler): LockedItems {
        val size = lock.write {
            if (scanMode.recomputeItems || items.isEmpty()) {
                val inputInode = ctx.getInode(path)
                if (!inputInode.inode0.contentPermission.canDirectoryGet && !inputInode.inode0.isFile) {
                    errorHandler.onError("""Input "$path" not available.""")
                    ctx.scannedInputs.remove(this)
                    previousLastModified = null
                    items.clear()
                    paths.clear()
                } else if (ctx.scannedInputs.add(this) || scanMode.ignoreScannedInputs) {
                    val currentLastModified = if (scanMode.lastModifiedEnabled) getCurrentLastModified(ctx, inputInode, errorHandler) else null
                    if (scanMode.ignoreLastModified || currentLastModified == null || currentLastModified != previousLastModified) {
                        previousLastModified = currentLastModified
                        computeItems(ctx, inputInode, registry, errorHandler)
                    }
                }
            }
            items.size
        }
        return LockedItems(lock, items, size)
    }

    fun getItemLocked(path: SafePath): Item? = lock.read { paths[path] }

    fun addItemLocked(ctx: RequestCtx, inode: PathCondition.ExternalInode<out Tag?>, registry: TagRegistry.Phase2, errorHandler: ErrorHandler) =
        lock.write { addItem(ctx, inode, registry, errorHandler) }

    private fun addItem(ctx: RequestCtx, inode: PathCondition.ExternalInode<out Tag?>, registry: TagRegistry.Phase2, errorHandler: ErrorHandler) {
        val contentInodes = LinkedList<Inode1<*>>()
        val item = Item.create(
            registry, inode.inode1, inode.payload, this, path.absoluteString.length,
            { getItemContent(ctx, inode.inode1, contentInodes::add, errorHandler) },
            errorHandler, inode.time,
        )
        items += item
        setPaths(inode.inode1, item)  // ContentVisitor does not visit item inode itself.
        for (contentInode in contentInodes) {
            setPaths(contentInode, item)
        }
    }

    fun getItemContent(ctx: RequestCtx, inode: Inode1<*>, onContentInode: (Inode1<*>) -> Unit, errorHandler: ErrorHandler): CharSequence {
        val contentBuilder = StringBuilder()
        ItemContent.visit(
            ctx.createPathFinderCtx(true), inode,
            ContentVisitor(contentCondition, errorHandler, contentCondition.pruneNames, contentBuilder, onContentInode)
        )
        return contentBuilder
    }

    private fun setPaths(inode: Inode1<*>, item: Item) {
        paths[inode.inode0.path] = item
        for (samePath in inode.config.samePaths) {
            paths[samePath] = item
        }
    }

    fun deleteItemLocked(path: SafePath) {
        lock.write {
            val item = paths.remove(path)
            if (item != null) {
                items.remove(item)
                for (samePath in item.inode.config.samePaths) {
                    paths.remove(samePath)
                }
            }
        }
    }

    private fun getCurrentLastModified(ctx: RequestCtx, inputInode: Inode1<*>, errorHandler: ErrorHandler): FileTime? {
        var lastModified: FileTime? = null
        for (currentLastModified in getSequenceOfLastModified(ctx.createPathFinderCtx(true), inputInode, errorHandler)) {
            if (currentLastModified == null) {
                return null
            } else {
                lastModified = lastModified?.coerceAtLeast(currentLastModified) ?: currentLastModified
            }
        }
        return lastModified
    }

    private fun computeItems(ctx: RequestCtx, inputInode: Inode1<*>, registry: TagRegistry.Phase2, errorHandler: ErrorHandler) {
        items.clear()
        paths.clear()
        timeDirectories.clear()
        for (inode in condition.getSequenceOfDescendants(ctx.createPathFinderCtx(true), inputInode, createPathConditionParameter(ctx, registry, errorHandler))) {
            when (inode) {
                is PathCondition.ExternalInode -> addItem(ctx, inode, registry, errorHandler)
                is PathCondition.InternalInode -> Unit
            }
        }
    }

    private fun createPathConditionParameter(ctx: RequestCtx, registry: TagRegistry, errorHandler: ErrorHandler): PathCondition.Parameter<out Tag.Mutable?> =
        if (inodeTag != null) {
            PathCondition.Parameter(createPayload = { parent, inode, component ->
                inodeTag.getOrCreateTag(ctx, registry, parent, inode, component, errorHandler)
            }, errorHandler = errorHandler, onTimeDirectory = ::addTimeDirectory, rootPayload = inodeTag.rootTag)
        } else {
            PathCondition.Parameter(
                createPayload = PathCondition.Parameter.createNullPayload,
                errorHandler = errorHandler,
                onTimeDirectory = ::addTimeDirectory,
                rootPayload = null
            )
        }

    private fun addTimeDirectory(inode: Inode1<*>): Boolean = timeDirectories.add(inode.inode0.path)
    fun isTimeDirectoryLocked(path: SafePath): Boolean = lock.read { path in timeDirectories }

    private fun getSequenceOfLastModified(ctx: PathFinder.Ctx, inputInode: Inode1<*>, errorHandler: ErrorHandler): Sequence<FileTime?> =
        sequence {
            for (inode in condition.getSequenceOfDescendants(
                ctx, inputInode,
                PathCondition.Parameter(createPayload = PathCondition.Parameter.createNullPayload, errorHandler = errorHandler, rootPayload = null, yieldInternal = true),
            )) {
                yield(inode.inode1.inode0.lastModified)
                if (inode.inode1.inode0.contentOperation.canDirectoryGet) {
                    for (contentInode in contentCondition.getSequenceOfDescendants(
                        ctx, inode.inode1,
                        PathCondition.Parameter(
                            createPayload = PathCondition.Parameter.createNullPayload,
                            errorHandler = errorHandler,
                            evaluatePatterns = false,
                            rootPayload = null,
                            yieldInternal = true
                        )
                    )) {
                        yield(contentInode.inode1.inode0.lastModified)
                    }
                }
            }
        }

    private class ContentVisitor(
        override val condition: PathCondition,
        override val errorHandler: ErrorHandler,
        override val pruneNames: Set<String>,
        private val contentBuilder: StringBuilder,
        private val onContentInode: (Inode1<*>) -> Unit,
    ) : ItemContent.Visitor {
        override fun onDirectory(inode: Inode1<*>): Boolean {
            Inode0.getChildrenAsTextTo(contentBuilder, inode.inode0)
            onContentInode(inode)
            return true
        }

        override fun onFile(inode: Inode1<*>): Boolean {
            contentBuilder.appendLine(inode.inode0.text)
            onContentInode(inode)
            return true
        }

        override fun onName(inode: Inode1<*>): Boolean {
            contentBuilder.appendLine(inode.inode0.path.name)
            onContentInode(inode)
            return true
        }
    }
}