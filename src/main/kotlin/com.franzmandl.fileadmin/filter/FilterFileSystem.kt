package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.IteratorUtil
import com.franzmandl.fileadmin.common.RegexUtil
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.*
import org.apache.commons.lang3.StringUtils
import org.springframework.http.MediaType
import java.nio.file.attribute.FileTime
import java.util.*

class FilterFileSystem(
    time: FileTime?,
    ctx: FilterCtx,
) : VirtualFileSystem {
    var time: FileTime? = time
        private set
    var ctx: FilterCtx = ctx
        private set

    fun setLocked(time: FileTime, ctx: FilterCtx): FilterFileSystem {
        synchronized(this) {
            this.time = time
            this.ctx = ctx
        }
        return this
    }

    override fun getInode(finder: PathFinder, remaining: List<String>): Inode1<*> {
        val filterStrings = LinkedList<String>()
        val systemStrings = LinkedList<String>()
        splitRemaining(finder.ctx.request.application.system.directoryName, remaining, filterStrings, systemStrings)
        val systemStringsIterator = systemStrings.iterator()
        return when (IteratorUtil.nextOrNull(systemStringsIterator)) {
            null -> {
                if (finder.ctx.filterIsScanning) {
                    finder.build(VirtualDirectory.createFromFinderAndChildren(finder, TagTreeOperation(this, false), setOf()), null)
                } else {
                    val result = ctx.filter(finder.ctx.request, finder.destination, filterStrings, finder.ctx)
                    finder.build(
                        VirtualDirectory.createFromFinderAndChildSet(
                            finder,
                            TagTreeOperation(this, result.canRename),
                            result.childSet
                        ), result
                    )
                }
            }

            systemRoot -> when (IteratorUtil.nextOrNull(systemStringsIterator)) {
                null -> {
                    ctx.scanItems(finder.ctx.request, CommandId.GetSystemRoot, finder.ctx)
                    finder.build(
                        VirtualDirectory.createFromFinderAndNames(
                            finder, VirtualDirectory.TreeOperation.Default, listOf(
                                systemAllTagsTxt,
                                systemCommonTagsTxt,
                                systemUnknownTagsJson,
                                systemUnknownTagsTxt,
                                systemUnusedTagsTxt,
                            )
                        ), null
                    )
                }

                systemAllTagsTxt ->
                    finder.build(
                        VirtualFile.createFromFinder(
                            finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                            collectPlainTagNames(getAllTags(finder.ctx))
                        ), null
                    )

                systemCommonTagsTxt -> {
                    val result = ctx.filter(finder.ctx.request, finder.destination, filterStrings, finder.ctx)
                    finder.build(
                        VirtualFile.createFromFinder(
                            finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                            collectPlainTagNames(ctx.filterNames(result.childSet.items, result.filters).commonTags)
                        ), null
                    )
                }

                systemUnknownTagsJson ->
                    finder.build(
                        VirtualFile.createFromFinder(
                            finder, VirtualFile.TreeOperation.Default, MediaType.APPLICATION_JSON_VALUE,
                            collectJsonTagNames(getUnknownTags(finder.ctx))
                        ), null
                    )

                systemUnknownTagsTxt ->
                    finder.build(
                        VirtualFile.createFromFinder(
                            finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                            collectPlainTagNames(getUnknownTags(finder.ctx))
                        ), null
                    )

                systemUnusedTagsTxt ->
                    finder.build(
                        VirtualFile.createFromFinder(
                            finder, VirtualFile.TreeOperation.Default, MediaType.TEXT_PLAIN_VALUE,
                            collectPlainTagNames(getUnusedTags(finder.ctx))
                        ), null
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
                systemStrings += systemRoot
                break
            }
            filterStrings += current
        }
        while (iterator.hasNext()) {
            systemStrings += iterator.next()
        }
    }

    private fun collectJsonTagNames(tags: Iterable<Tag>): ByteArray {
        val builder = StringBuilder().appendLine("  [")
        for (tag in tags.sortedBy(::sortSelector)) {
            builder.append("""    {"_type": "TagVersion1", "name": "#""").append(tag.name).append('"').appendLine("},")
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
        ctx.scanItems(pathFinderCtx.request, CommandId.GetAllTags, pathFinderCtx)
        return ctx.registry.tags.values
    }

    private fun getUnknownTags(pathFinderCtx: PathFinder.Ctx): Iterable<Tag> {
        ctx.scanItems(pathFinderCtx.request, CommandId.GetUnknownTags, pathFinderCtx)
        return ctx.registry.systemTags.unknown.getSequenceOfChildren(Tag.ChildrenParameter.all).asIterable()
    }

    private fun getUnusedTags(pathFinderCtx: PathFinder.Ctx): Set<Tag> {
        val items = ctx.getLockedItemsList(pathFinderCtx.request, CommandId.GetUnusedTags, pathFinderCtx).toList() // Materialize before calculating unusedTags.
        val unusedTags = ctx.registry.tags.values.toMutableSet()
        for (item in items) {
            unusedTags.removeAll(item.allTags.all)
        }
        return unusedTags
    }

    fun requiresAction(requestCtx: RequestCtx, errorHandler: ErrorHandler): Boolean {
        if (ctx.getLockedItemsList(requestCtx, CommandId.RequiresAction, errorHandler).find { ctx.registry.systemTags.lostAndFound in it.allTags.all } != null) {
            return true
        }
        for (child in ctx.registry.systemTags.unknown.getSequenceOfChildren(Tag.ChildrenParameter.all)) {
            return true
        }
        return false
    }

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        const val aliasDeliminator = '='
        const val childPrefix = '\''
        const val childPrefixString = childPrefix.toString()
        const val operatorPrefix = ','
        const val operatorEnd = ')'
        const val operatorEndString = operatorEnd.toString()
        const val priorityPrefix = '!'
        const val priorityPrefixString = priorityPrefix.toString()
        const val tagPrefix = '#'
        const val tagPrefixString = tagPrefix.toString()

        val compareOperators = CompareOperator.entries.associateBy { it.text }
        const val operator = "operator"
        const val operatorElse = "else"
        const val operatorEvaluate = "evaluate"
        const val operatorIntersect = "intersect"
        const val operatorMax = "max("
        val operatorMaxSuggestions = setOf("100", "250", "500")
        const val operatorNot = "not"
        const val operatorTagReason = "tagReason"
        val operatorTagReasons = TagFilter.Reason.entries.associateBy { it.name }
        const val operatorTagRelationship = "tagRelationship"
        val operatorTagRelationships = TagFilter.Relationship.entries.associateBy { it.name }
        const val operatorText = "text"
        val operatorTextReasons = TextFilter.Reason.entries.associateBy { it.name + "(" }
        const val operatorTime = "time"

        const val systemRoot = ""
        const val systemAllTagsTxt = "allTags.txt"
        const val systemCommonTagsTxt = "commonTags.txt"
        const val systemUnknownTagsJson = "unknownTags.json"
        const val systemUnknownTagsTxt = "unknownTags.txt"
        const val systemUnusedTagsTxt = "unusedTags.txt"

        val operatorsWithoutElse =
            listOf(operatorEvaluate, operatorIntersect, operatorMax, operatorNot, operatorTagReason, operatorTagRelationship, operatorText, operatorTime)
        val operatorsWithElse = operatorsWithoutElse + listOf(operatorElse)
        val operators = listOf(operator, operatorEvaluate, operatorIntersect)
        val operatorsWithPrefix = operators.map { operatorPrefix + it }

        val tagNameEndingLetterRegex = Regex("""[\p{L}\d_]""")
        val tagNameRegex = Regex("""(?:\p{L}+|\d+[\p{L}_])$tagNameEndingLetterRegex*""")
        val tagRegex = Regex("${Regex.escape(tagPrefixString)}($tagNameRegex)")

        val urlWithFragmentRegex = Regex("""[^:/?#"()\s]+://[^/?#"()\s]+[^?#"\s]*(?:\?[^#"\s]*)?#[^"\s]*""")
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

        fun trimPrefix(name: String, errorHandler: ErrorHandler): String =
            if (!name.startsWith(tagPrefixString)) {
                errorHandler.onError("""Illegal tag name: "$name" does not start with $tagPrefixString.""")
                name
            } else {
                name.substring(1)
            }

        private fun sortSelector(tag: Tag) =
            tag.sortableName

        private val patterns = listOf<Pair<Regex, (MatchResult) -> CharSequence>>(
            Regex("""&""") to { "and" },
            Regex("""[$]""") to { "s" },
            Regex("""[,.] """) to { " " },
            Regex("""([^\p{L}\d_]+)(\p{L}?)""") to { if (it.groupValues[1].length > 1) "_${it.groupValues[2]}" else it.groupValues[2].uppercase() },
        )

        fun toTagName(part: String): String {
            var result = part
            for ((pattern, transform) in patterns) {
                result = result.replace(pattern, transform)
            }
            return result
        }

        fun replaceUrlsWithFragment(content: CharSequence): String =
            content.replace(urlWithFragmentRegex) { urlWithFragmentReplacementString.repeat(it.value.length) }

        fun getSequenceOfConsecutiveTagNames(value: String, startIndex: Int): Sequence<List<StringRange<Nothing?>>> =
            if (startIndex > value.length) {
                // Happens when the parent path of an input directory with minDepth = 0 (=yielding itself) is visited.
                sequenceOf()
            } else {
                RegexUtil.getSequenceOfGroupedConsecutiveMatchResults(tagRegex.findAll(value.substring(startIndex))) {
                    val nameGroup = it.groups[1]!!
                    StringRange(nameGroup.value, startIndex + nameGroup.range.first, startIndex + nameGroup.range.last, null)
                }
            }
    }
}