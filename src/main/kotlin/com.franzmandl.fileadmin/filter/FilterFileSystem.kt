package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.HttpException
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
        return if (systemStrings.isEmpty()) {
            val result = ctx.filter(finder.destination, filterStrings, finder.ctx.errors::add)
            result.children.add(finder.destination.resolve(systemDirectoryName))
            VirtualDirectory.createFromFinder(finder, TagTreeOperation(this, result.canRename), result.children)
        } else {
            when (systemStrings) {
                listOf(systemRoot) -> {
                    ctx.scanItems(false, finder.ctx.errors::add)
                    VirtualDirectory.createFromFinderAndNames(
                        finder, VirtualDirectory.TreeOperation.Default, listOf(
                            systemAllTags,
                            systemAllTagsTxt,
                            systemCommonTags,
                            systemCommonTagsTxt,
                            systemUnknownTags,
                            systemUnknownTagsJson,
                            systemUnknownTagsTxt,
                            systemUnusedTags,
                            systemUnusedTagsTxt,
                        )
                    )
                }

                listOf(systemRoot, systemAllTags) ->
                    VirtualDirectory.createFromFinderAndNames(
                        finder, VirtualDirectory.TreeOperation.Default,
                        getAllTags(finder.ctx.errors::add).map { it.name }
                    )

                listOf(systemRoot, systemAllTagsTxt) ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(getAllTags(finder.ctx.errors::add))
                    )

                listOf(systemRoot, systemCommonTags) ->
                    VirtualDirectory.createFromFinderAndNames(
                        finder, VirtualDirectory.TreeOperation.Default,
                        ctx.filter(finder.destination, filterStrings, finder.ctx.errors::add).commonTags.map { it.name }
                    )

                listOf(systemRoot, systemCommonTagsTxt) ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(ctx.filter(finder.destination, filterStrings, finder.ctx.errors::add).commonTags)
                    )

                listOf(systemRoot, systemUnknownTagsJson) ->
                    VirtualDirectory.createFromFinderAndNames(
                        finder, VirtualDirectory.TreeOperation.Default,
                        getUnknownTags(finder.ctx.errors::add).map { it.name }
                    )

                listOf(systemRoot, systemUnknownTagsJson) ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.APPLICATION_JSON_VALUE,
                        collectJsonTagNames(getUnknownTags(finder.ctx.errors::add))
                    )

                listOf(systemRoot, systemUnknownTagsTxt) ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(getUnknownTags(finder.ctx.errors::add))
                    )

                listOf(systemRoot, systemUnusedTags) ->
                    VirtualDirectory.createFromFinderAndNames(
                        finder, VirtualDirectory.TreeOperation.Default,
                        getUnusedTags(finder.ctx.errors::add).map { it.name }
                    )

                listOf(systemRoot, systemUnusedTagsTxt) ->
                    VirtualFile.createFromFinder(
                        finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                        collectPlainTagNames(getUnusedTags(finder.ctx.errors::add))
                    )

                else ->
                    VirtualDirectory.createFromFinder(finder, TagTreeOperation(this, canRenameSystemStrings(systemStrings)), setOf())
            }
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

    private fun canRenameSystemStrings(systemStrings: List<String>): Boolean {
        return (ctx.registry.getTag(systemStrings.lastOrNull() ?: return false) ?: return false).parameter.canRename
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

    private fun getAllTags(onError: (String) -> Unit): Iterable<Tag> {
        ctx.scanItems(true, onError)
        return ctx.registry.tags.values
    }

    private fun getUnknownTags(onError: (String) -> Unit): Set<Tag> {
        ctx.scanItems(true, onError)
        return ctx.registry.tagUnknown.children
    }

    private fun getUnusedTags(onError: (String) -> Unit): Set<Tag> {
        val items = ctx.getSequenceOfItems(true, onError).toList()  // Materialize before calculating unusedTags.
        val unusedTags = ctx.registry.tags.values.toMutableSet()
        for (item in items) {
            unusedTags.removeAll(item.allTags.all)
        }
        return unusedTags
    }

    fun requiresAction(onError: (String) -> Unit): Boolean {
        for (item in ctx.getSequenceOfItems(true, onError)) {
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
        const val systemAllTags = "allTags"
        const val systemAllTagsTxt = "allTags.txt"
        const val systemCommonTags = "commonTags"
        const val systemCommonTagsTxt = "commonTags.txt"
        const val systemUnknownTags = "unknownTags"
        const val systemUnknownTagsJson = "unknownTags.json"
        const val systemUnknownTagsTxt = "unknownTags.txt"
        const val systemUnusedTags = "unusedTags"
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