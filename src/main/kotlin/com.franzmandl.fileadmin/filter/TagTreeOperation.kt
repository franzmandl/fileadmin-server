package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.SafePath
import com.franzmandl.fileadmin.vfs.VirtualDirectory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class TagTreeOperation(
    private val system: FilterFileSystem,
    override val canInodeRename: Boolean,
) : VirtualDirectory.TreeOperation {
    override fun move(ctx: RequestCtx, inode: Inode, target: Inode) {
        if (!canInodeRename) {
            throw HttpException.notSupported().move(inode.path).to(target.path).build()
        }
        if (inode.path.parent != target.path.parent) {
            throw HttpException.badRequest("A tag can only be renamed.")
        }
        val oldName = FilterFileSystem.trimName(inode.path.name)
        val newName = FilterFileSystem.trimName(target.path.name)
        if (!FilterFileSystem.tagNameRegex.matches(newName)) {
            throw HttpException.badRequest("The new name is no valid name for a tag.")
        }
        val replaceCtx = ReplaceCtx(system, ctx, oldName, newName)
        if (replaceCtx.getTag() == null) {
            throw HttpException.badRequest("The tag does not exist.")
        }
        try {
            replaceConfigFile(replaceCtx)
            replaceContent(replaceCtx.reset())
            replaceName(replaceCtx.reset())
            replaceParentPath(replaceCtx.reset())
        } catch (e: Exception) {
            throw HttpException.badRequest(replaceCtx.revert(e))
        }
        if (!replaceCtx.condition()) {
            throw HttpException.badRequest("Too many occurrences to be moved all at once. Repeat again.")
        }
    }

    private class ReplaceCtx(
        private val system: FilterFileSystem,
        val request: RequestCtx,
        val oldName: String,
        newName: String,
    ) {
        private val commands = LinkedList<Command>()
        private var iterationCount = 0
        private val logger: Logger = LoggerFactory.getLogger(ReplaceCtx::class.java)
        private val oldHash = "${FilterFileSystem.tagPrefix}$oldName"
        private val newHash = "${FilterFileSystem.tagPrefix}$newName"
        private var lastItemsSize = 0

        fun addCommand(command: Command) {
            try {
                command.apply()
            } catch (e: Exception) {
                request.application.incrementLogId().let {
                    logger.warn("$it during ${command.applyStringLong}", e)
                    throw HttpException.badRequest("Failed to apply ${command.applyStringShort} (see log $it): ${HttpException.getMessage(e)}")
                }
            }
            commands.add(0, command)
        }

        fun condition(): Boolean =
            iterationCount++ < request.application.maxIterationCount

        fun getTag(): Tag? {
            val inode = request.getInode(system.ctx.output)  // Trigger a database reload if necessary.
            val filterCtx = (inode.config.filter ?: throw HttpException.badRequest("Filter directory lost.")).ctx
            if (filterCtx != system.ctx) {
                throw HttpException.badRequest("Illegal state: Contexts are not the same.")
            }
            return filterCtx.registry.getTag(oldName)
        }

        fun getNextItem(reason: TagFilter.Reason): Item? {
            val items = system.ctx.filterItems(listOf(TagFilter(getTag() ?: return null, reason, TagFilter.Relationship.Self))) {
                throw HttpException.badRequest(it)
            }
            if (items.size >= lastItemsSize) {
                // Note: False positive if an item get the same tag twice from its parentPath on two different levels.
                throw HttpException.badRequest("Endless detected: Items size did not decrease (${items.size} >= $lastItemsSize).")
            }
            lastItemsSize = items.size
            return items.firstOrNull()
        }

        fun replaceString(string: String): String =
            string.replace(oldHash, newHash)

        fun reset(): ReplaceCtx {
            lastItemsSize = Int.MAX_VALUE
            return this
        }

        fun revert(e: Exception): String {
            val builder = StringBuilder().appendLine("An error occurred during renaming. This message is also logged.")
            request.application.incrementLogId().let {
                logger.warn(it, e)
                builder.appendLine("Cause (see log $it): ${HttpException.getMessage(e)}")
            }
            var reversionSuccessful = true
            for (command in commands) {
                try {
                    command.revert()
                } catch (e: Exception) {
                    reversionSuccessful = false
                    request.application.incrementLogId().let {
                        logger.warn("$it during ${command.revertStringLong}", e)
                        builder.appendLine("Failed to revert ${command.revertStringShort} (see log $it): ${HttpException.getMessage(e)}")
                    }
                }
            }
            builder.appendLine(
                if (reversionSuccessful) "Reversion successful."
                else "Reversion incomplete."
            )
            return builder.toString()
        }
    }

    private fun replaceConfigFile(ctx: ReplaceCtx) {
        while (ctx.condition()) {
            val configFiles = (ctx.getTag() ?: break).configFiles
            if (configFiles.isEmpty()) {
                break
            }
            for ((_, configFile) in configFiles) {
                val oldText = configFile.text
                val newText = ctx.replaceString(oldText)
                ctx.addCommand(SetTextCommand(configFile, oldText, newText))
            }
        }
    }

    private fun replaceContent(ctx: ReplaceCtx) {
        while (ctx.condition()) {
            val item = ctx.getNextItem(TagFilter.Reason.Content) ?: break
            if (item.inode.contentPermission.canFileGet) {
                val oldText = item.inode.text
                val newText = ctx.replaceString(oldText)
                ctx.addCommand(SetTextCommand(item.inode, oldText, newText))
            } else if (item.inode.contentPermission.canDirectoryGet) {
                for (oldPath in item.inode.children) {
                    val newInodeName = ctx.replaceString(oldPath.name)
                    if (newInodeName == oldPath.name) {
                        continue
                    }
                    val oldInode = ctx.request.getInode(oldPath)
                    val newPath = oldInode.path.resolveSibling(newInodeName)
                    val newInode = ctx.request.getInode(newPath)
                    ctx.addCommand(MoveCommand(ctx.request, oldInode, newInode))
                    break
                }
            }
        }
    }

    private fun replaceName(ctx: ReplaceCtx) {
        while (ctx.condition()) {
            val item = ctx.getNextItem(TagFilter.Reason.Name) ?: break
            val oldInode = item.inode
            val newPath = oldInode.path.resolveSibling(ctx.replaceString(oldInode.path.name))
            val newInode = ctx.request.getInode(newPath)
            ctx.addCommand(MoveCommand(ctx.request, oldInode, newInode))
        }
    }

    private fun replaceParentPath(ctx: ReplaceCtx) {
        while (ctx.condition()) {
            val item = ctx.getNextItem(TagFilter.Reason.ParentPath) ?: break
            var oldPath: SafePath? = item.inode.path
            while (oldPath != null) {
                oldPath = oldPath.parent ?: break
                if (oldPath.absoluteString.length <= item.parentPathStartIndex) {
                    throw HttpException.badRequest("Illegal state: '$oldPath' should never have been found by getNextItem (${oldPath.absoluteString.length} <= ${item.parentPathStartIndex}).")
                }
                val newInodeName = ctx.replaceString(oldPath.name)
                if (newInodeName == oldPath.name) {
                    continue
                }
                val oldInode = ctx.request.getInode(oldPath)
                val newPath = oldInode.path.resolveSibling(newInodeName)
                val newInode = ctx.request.getInode(newPath)
                ctx.addCommand(MoveCommand(ctx.request, oldInode, newInode))
                break
            }
        }
    }

    private sealed interface Command {
        fun apply()
        val applyStringLong: String
        val applyStringShort: String
        fun revert()
        val revertStringLong: String
        val revertStringShort: String
    }

    private class MoveCommand(
        private val ctx: RequestCtx,
        private val oldInode: Inode,
        private val newInode: Inode,
    ) : Command {
        override fun apply() {
            oldInode.move(ctx, newInode)
        }

        override val applyStringLong: String
            get() = "Move '${oldInode.path.absoluteString}' -> '${newInode.path.absoluteString}'"

        override val applyStringShort: String
            get() = applyStringLong

        override fun revert() {
            newInode.move(ctx, oldInode)
        }

        override val revertStringLong: String
            get() = "Move '${newInode.path.absoluteString}' -> '${oldInode.path.absoluteString}'"

        override val revertStringShort: String
            get() = revertStringLong
    }

    private class SetTextCommand(
        private val inode: Inode,
        private val oldText: String,
        private val newText: String,
    ) : Command {
        override fun apply() {
            inode.setText(newText)
        }

        override val applyStringLong: String
            get() = "$applyStringShort '$oldText' -> '$newText'"

        override val applyStringShort: String
            get() = "SetText ${inode.path.absoluteString}"

        override fun revert() {
            inode.setText(oldText)
        }

        override val revertStringLong: String
            get() = "$revertStringShort '$newText' -> '$oldText'"

        override val revertStringShort: String
            get() = "SetText ${inode.path.absoluteString}"
    }
}