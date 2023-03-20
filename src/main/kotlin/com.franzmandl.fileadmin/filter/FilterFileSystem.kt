package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.*
import org.apache.commons.lang3.StringUtils
import org.springframework.http.MediaType
import java.nio.file.attribute.FileTime
import java.util.*

class FilterFileSystem(
    var time: FileTime?,
    var ctx: FilterCtx,
) : VirtualFileSystem {
    override fun getInode(finder: PathFinder, remaining: List<String>): Inode {
        val systemDirectoryName = finder.ctx.request.application.systemDirectoryName
        val filterStrings = LinkedList<String>()
        val systemStrings = LinkedList<String>()
        splitRemaining(systemDirectoryName, remaining, filterStrings, systemStrings)
        val systemStringsIterator = systemStrings.iterator()
        return when (CommonUtil.nextOrNull(systemStringsIterator)) {
            null -> {
                val result = ctx.filter(finder.ctx.request, finder.destination, filterStrings, finder.ctx.errors::add)
                finder.filterHighlightTags = result.getHighlightTags()
                if (result.children.isNotEmpty()) {
                    result.children.add(finder.destination.resolve(systemDirectoryName))
                }
                VirtualDirectory.createFromFinder(finder, TagTreeOperation(this, result.canRename), result.children)
            }

            systemRoot -> when (CommonUtil.nextOrNull(systemStringsIterator)) {
                null -> {
                    ctx.scanItems(finder.ctx.request, false, finder.ctx.errors::add)
                    VirtualDirectory.createFromFinderAndNames(
                        finder, VirtualDirectory.TreeOperation.Default, listOf(
                            systemAllTagsTxt,
                            systemCommonTagsTxt,
                            systemUnknownTagsJson,
                            systemUnknownTagsTxt,
                            systemUnusedTagsTxt,
                        )
                    )
                }

                systemAllTagsTxt ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(getAllTags(finder.ctx))
                    )

                systemCommonTagsTxt ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(ctx.filter(finder.ctx.request, finder.destination, filterStrings, finder.ctx.errors::add).commonTags)
                    )

                systemUnknownTagsJson ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.APPLICATION_JSON_VALUE,
                        collectJsonTagNames(getUnknownTags(finder.ctx))
                    )

                systemUnknownTagsTxt ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(getUnknownTags(finder.ctx))
                    )

                systemUnusedTagsTxt ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(getUnusedTags(finder.ctx))
                    )

                else -> throw HttpException.badRequest("Illegal path: ${finder.destination}")
            }

            else -> throw HttpException.badRequest("Illegal path: ${finder.destination}")
        }
    }

    private fun splitRemaining(systemDirectoryName: String, remaining: List<String>, filterStrings: MutableList<String>, systemStrings: MutableList<String>) {
        val iterator = remaining.iterator()
        while (iterator.hasNext()) {
            val current = iterator.next()
            if (current == systemDirectoryName) {
                systemStrings.add(systemRoot)
                break
            }
            filterStrings.add(current)
        }
        while (iterator.hasNext()) {
            systemStrings.add(iterator.next())
        }
    }

    private fun collectJsonTagNames(tags: Iterable<Tag>): ByteArray {
        val builder = StringBuilder().appendLine("  [")
        for (tag in tags.sortedBy(::sortSelector)) {
            builder.append("    {\"_type\": \"TagVersion1\", \"name\": \"#").append(tag.name).appendLine("\"},")
        }
        return builder.appendLine("    null" /* Makes JSON valid. */).appendLine("  ]").toString().toByteArray()
    }

    private fun collectPlainTagNames(tags: Iterable<Tag>): ByteArray {
        val builder = StringBuilder()
        for (tag in tags.sortedBy(::sortSelector)) {
            builder.appendLine(tag.name)
        }
        return builder.toString().toByteArray()
    }

    private fun getAllTags(pathFinderCtx: PathFinder.Ctx): Iterable<Tag> {
        ctx.scanItems(pathFinderCtx.request, true, pathFinderCtx.errors::add)
        return ctx.registry.tags.values
    }

    private fun getUnknownTags(pathFinderCtx: PathFinder.Ctx): Set<Tag> {
        ctx.scanItems(pathFinderCtx.request, true, pathFinderCtx.errors::add)
        return ctx.registry.tagUnknown.children
    }

    private fun getUnusedTags(pathFinderCtx: PathFinder.Ctx): Set<Tag> {
        val items = ctx.getSequenceOfItems(pathFinderCtx.request, true, pathFinderCtx.errors::add).toList()  // Materialize before calculating unusedTags.
        val unusedTags = ctx.registry.tags.values.toMutableSet()
        for (item in items) {
            unusedTags.removeAll(item.allTags.all)
        }
        return unusedTags
    }

    fun requiresAction(requestCtx: RequestCtx, onError: (String) -> Unit): Boolean {
        for (item in ctx.getSequenceOfItems(requestCtx, true, onError)) {
            if (ctx.registry.tagLostAndFound in item.allTags.all) {
                return true
            }
        }
        return ctx.registry.tagUnknown.children.isNotEmpty()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        const val aliasDeliminator = '='
        const val childPrefix = '\''
        const val childPrefixString = childPrefix.toString()
        const val descendantsPrefix = '@'
        const val descendantsPrefixString = descendantsPrefix.toString()
        const val operatorPrefix = ','
        const val priorityPrefix = '!'
        const val priorityPrefixString = priorityPrefix.toString()
        const val tagPrefix = '#'
        const val tagPrefixString = tagPrefix.toString()

        const val operator = "operator"
        const val operatorElse = "else"
        const val operatorEvaluate = "evaluate"
        const val operatorNot = "not"
        val operatorReasons = TagFilter.Reason.values().associateBy { "reason${it.name}" }
        val operatorRelationships = TagFilter.Relationship.values().associateBy { "relationship${it.name}" }

        const val systemRoot = ""
        const val systemAllTagsTxt = "allTags.txt"
        const val systemCommonTagsTxt = "commonTags.txt"
        const val systemUnknownTagsJson = "unknownTags.json"
        const val systemUnknownTagsTxt = "unknownTags.txt"
        const val systemUnusedTagsTxt = "unusedTags.txt"

        val tagNameRegex = Regex("(?:\\p{L}+|\\d+[\\p{L}_])[\\p{L}\\d_]*")
        val tagRegex = Regex("${Regex.escape(tagPrefixString)}(${Regex.escape(descendantsPrefixString)})?($tagNameRegex)")

        /** Fragment is not optional. A general URL pattern would be "${urlWithFragmentRegex}?". */
        val urlWithFragmentRegex = Regex("[^:/?#\"()\\s]+://[^/?#\"()\\s]+[^?#\"\\s]*(?:\\?[^#\"\\s]*)?(?:#[^\"\\s]*)")
        const val urlWithFragmentReplacement = 'u'
        const val urlWithFragmentReplacementString = urlWithFragmentReplacement.toString()

        fun toComparableName(name: String) =
            toSortableName(name).replace("_", "")

        fun toSortableName(name: String): String =
            StringUtils.stripAccents(name.lowercase())

        fun trimName(name: String): String =
            tagNameRegex.find(name)?.value ?: ""

        fun isValidName(name: String): Boolean =
            tagNameRegex.matches(name)

        fun validateName(name: String): String =
            if (isValidName(name)) name else throw HttpException.badRequest("Illegal name: '$name' matches anti pattern.")

        private fun sortSelector(tag: Tag) =
            tag.sortableName
    }
}