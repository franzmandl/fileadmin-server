package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.*
import org.slf4j.LoggerFactory
import java.util.*

class TagTreeOperation(
    private val system: FilterFileSystem,
    override val canInodeRename: Boolean,
) : VirtualDirectory.TreeOperation {
    override fun move(ctx: RequestCtx, inode: Inode0, target: Inode1<*>) {
        if (!canInodeRename) {
            throw HttpException.notSupported().move(inode.path).to(target.inode0.path).build()
        }
        if (inode.path.parent != target.inode0.path.parent) {
            throw HttpException.badRequest("A tag can only be renamed.")
        }
        val oldName = FilterFileSystem.trimName(inode.path.name)
        val newName = FilterFileSystem.trimName(target.inode0.path.name)
        if (!FilterFileSystem.isValidName(newName)) {
            throw HttpException.badRequest("The new name is no valid name for a tag.")
        }
        val replaceCtx = ReplaceCtx(system, ctx, oldName, newName)
        if (replaceCtx.getTag() == null) {
            throw HttpException.badRequest("The tag does not exist.")
        }
        try {
            replaceConfigFile(replaceCtx)
            replaceContent(replaceCtx)
            replaceName(replaceCtx)
            replaceParentPath(replaceCtx)
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
        private val logger = LoggerFactory.getLogger(this::class.java)

        // Also see https://www.regular-expressions.info/lookaround.html
        private val oldHashRegex = Regex("${Regex.escape("${FilterFileSystem.tagPrefix}$oldName")}(?!${FilterFileSystem.tagNameEndingLetterRegex})")
        private val newHash = "${FilterFileSystem.tagPrefix}$newName"

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
            iterationCount++ < request.application.system.maxIterationCount

        fun getTag(): Tag? {
            val inode = request.getInode(system.ctx.output)  // Trigger a database reload if necessary.
            return (inode.config.filter ?: throw HttpException.badRequest("Filter directory lost.")).ctx.also {
                if (it != system.ctx) {
                    throw HttpException.badRequest("Illegal state: Contexts are not the same.")
                }
            }.registry.getTag(oldName)
        }

        fun getNextItem(reason: TagFilter.Reason): Item? {
            val items = system.ctx.getLockedItemsList(request, CommandId.MoveTag, ErrorHandler.badRequest).toMutableList()
            return system.ctx.filterItems(request, items, listOf(TagFilter(getTag() ?: return null, reason, TagFilter.Relationship.Self)), ErrorHandler.badRequest).firstOrNull()
        }

        fun replaceString(string: String): String {
            // Having a lookaround in oldHashRegex to detect urlWithFragment performs much worse.
            var mutableUrl = FilterFileSystem.urlWithFragmentRegex.find(string)
            return string.replace(oldHashRegex, fun(old: MatchResult): String {
                while (true) {
                    val url = mutableUrl
                    when {
                        url == null -> return newHash
                        old.range.last < url.range.first -> return newHash
                        url.range.first <= old.range.first && old.range.last <= url.range.last -> return old.value
                        else -> mutableUrl = url.next()
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("Unreachable Code.")
            })
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

    private class ContentVisitor(
        private val ctx: ReplaceCtx,
        override val condition: PathCondition,
        override val pruneNames: Set<String>,
    ) : ItemContent.Visitor {
        override val errorHandler = ErrorHandler.badRequest

        override fun onDirectory(inode: Inode1<*>): Boolean {
            for (child in inode.inode0.children) {
                if (!move(child, null)) {
                    return false
                }
            }
            return true
        }

        override fun onFile(inode: Inode1<*>): Boolean {
            val oldText = inode.inode0.text
            val newText = ctx.replaceString(oldText)
            ctx.addCommand(SetTextCommand(inode.inode0, oldText, newText))
            return oldText === newText
        }

        override fun onName(inode: Inode1<*>): Boolean =
            move(inode.inode0.path, inode)

        private fun move(path: SafePath, inode: Inode1<*>?): Boolean {
            val newInodeName = ctx.replaceString(path.name)
            if (newInodeName === path.name) {
                return true
            }
            val oldInode = inode ?: ctx.request.getInode(path)
            val newPath = oldInode.inode0.path.resolveSibling(newInodeName)
            val newInode = ctx.request.getInode(newPath)
            ctx.addCommand(MoveCommand(ctx.request, oldInode, newInode))
            return false
        }
    }

    private fun replaceConfigFile(ctx: ReplaceCtx) {
        while (ctx.condition()) {
            val configFiles = (ctx.getTag() ?: break).getConfigFiles()
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
            ItemContent.visit(ctx.request.createPathFinderCtx(false), item.inode, ContentVisitor(ctx, item.input.contentCondition, item.input.contentCondition.pruneNames))
        }
    }

    private fun replaceName(ctx: ReplaceCtx) {
        while (ctx.condition()) {
            val item = ctx.getNextItem(TagFilter.Reason.Name) ?: break
            val oldInode = item.inode
            val newPath = oldInode.inode0.path.resolveSibling(ctx.replaceString(oldInode.inode0.path.name))
            val newInode = ctx.request.getInode(newPath)
            ctx.addCommand(MoveCommand(ctx.request, oldInode, newInode))
        }
    }

    private fun replaceParentPath(ctx: ReplaceCtx) {
        while (ctx.condition()) {
            val item = ctx.getNextItem(TagFilter.Reason.ParentPath) ?: break
            var oldPath: SafePath? = item.inode.inode0.path
            while (oldPath != null) {
                oldPath = oldPath.parent ?: break
                if (oldPath.absoluteString.length <= item.parentPathStartIndex) {
                    throw HttpException.badRequest("""Illegal state: "$oldPath" should never have been found by getNextItem (${oldPath.absoluteString.length} <= ${item.parentPathStartIndex}).""")
                }
                val newInodeName = ctx.replaceString(oldPath.name)
                if (newInodeName == oldPath.name) {
                    continue
                }
                val oldInode = ctx.request.getInode(oldPath)
                val newPath = oldInode.inode0.path.resolveSibling(newInodeName)
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
        private val oldInode: Inode1<*>,
        private val newInode: Inode1<*>,
    ) : Command {
        override fun apply() {
            oldInode.inode0.move(ctx, newInode)
        }

        override val applyStringLong: String
            get() = """Move "${oldInode.inode0.path.absoluteString}" -> "${newInode.inode0.path.absoluteString}"."""

        override val applyStringShort: String
            get() = applyStringLong

        override fun revert() {
            newInode.inode0.move(ctx, oldInode)
        }

        override val revertStringLong: String
            get() = """Move "${newInode.inode0.path.absoluteString}" -> "${oldInode.inode0.path.absoluteString}"."""

        override val revertStringShort: String
            get() = revertStringLong
    }

    private class SetTextCommand(
        private val inode: Inode0,
        private val oldText: String,
        private val newText: String,
    ) : Command {
        override fun apply() {
            inode.setText(newText)
        }

        override val applyStringLong: String
            get() = """$applyStringShort "$oldText" -> "$newText"."""

        override val applyStringShort: String
            get() = "SetText ${inode.path.absoluteString}"

        override fun revert() {
            inode.setText(oldText)
        }

        override val revertStringLong: String
            get() = """$revertStringShort "$newText" -> "$oldText"."""

        override val revertStringShort: String
            get() = "SetText ${inode.path.absoluteString}"
    }
}