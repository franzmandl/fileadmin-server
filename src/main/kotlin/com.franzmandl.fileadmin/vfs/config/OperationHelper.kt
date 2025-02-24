package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.Cursor
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.config.*
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.filter.TagRegistry
import com.franzmandl.fileadmin.vfs.PathFinder
import org.slf4j.LoggerFactory

class OperationHelper(
    private val ctx: PathFinder.Ctx,
    private val operation: ConfigOperation,
    private val registerTagHelper: RegisterTagHelper,
    private val registry: TagRegistry,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val applicationCtx = ctx.request.application
    private val lastTagOperand = findLastTagOperand()

    fun handleOperation() {
        val explicitParents = mutableListOf<Tag.Mutable>()
        for (aliases in operation.latest.parents ?: listOf()) {
            explicitParents += registerTagHelper.getOrCreateTags(
                registry,
                TagNameHelper.getSequenceOfAliases(ctx, operation.configFile.path, aliases, TagNameHelper.nameResolverWithValidation),
                operation.configFile, Tag.Parameter.placeholder, latest = null
            )
        }
        val cursor = Cursor.create(operation.latest.operands) ?: return
        var lastCompositeTagsList = initCompositeTags(explicitParents, cursor) ?: return
        while (!cursor.hasEnded) {
            when (val operand = cursor.current) {
                is TagOperandVersioned -> {
                    cursor.proceed()
                    val suffix = collectText(cursor)
                    val compositeTagsList = mutableListOf<List<CompositeTag>>()
                    for (tag in getAffectedTags(operand)) {
                        val twins = tag.getSequenceOfTwins(true).toList()
                        for (lastCompositeTags in lastCompositeTagsList) {
                            lastCompositeTags.mapTo(compositeTagsList) { compositeTag ->
                                val prefix = CollectedText(compositeTag.suggestMinimumLength, compositeTag.tag.name)
                                val compositeTags = createCompositeTags(operand, twins, prefix, suffix)
                                linkTags(operand, transformCompositeTags(lastCompositeTags), twins, transformCompositeTags(compositeTags), ctx)
                                compositeTags
                            }
                        }
                    }
                    lastCompositeTagsList = compositeTagsList
                }

                is TextOperandVersion1 -> handleUnexpectedTextOperand()
                null -> handleUnexpectedNullOperand()
            }
        }
    }

    private class CompositeTag(
        val suggestMinimumLength: Int,
        val tag: Tag.Mutable,
    )

    private class CollectedText(
        val suggestMinimumLength: Int,
        val text: String,
    )

    private fun collectText(cursor: Cursor<OperandVersioned?>): CollectedText {
        val builder = StringBuilder()
        var suggestMinimumLength = 0
        while (!cursor.hasEnded) {
            val current = cursor.current
            if (current != null && current.enabled != false) {
                when (current) {
                    is TagOperandVersioned -> break
                    is TextOperandVersion1 -> {
                        builder.append(current.text)
                        suggestMinimumLength += current.suggestMinimumLength ?: 0
                    }
                }
            }
            cursor.proceed()
        }
        return CollectedText(suggestMinimumLength, builder.toString())
    }

    private fun initCompositeTags(
        explicitParents: List<Tag.Mutable>,
        cursor: Cursor<OperandVersioned?>,
    ): List<List<CompositeTag>>? {
        val prefix = collectText(cursor)
        if (cursor.hasEnded) {
            return null
        }
        val compositeTagsList = mutableListOf<List<CompositeTag>>()
        when (val operand = cursor.current) {
            is TagOperandVersioned -> {
                cursor.proceed()
                val suffix = collectText(cursor)
                getAffectedTags(operand).mapTo(compositeTagsList) { tag ->
                    val twins = tag.getSequenceOfTwins(true).toList()
                    val compositeTags = createCompositeTags(operand, twins, prefix, suffix)
                    linkTags(operand, explicitParents, twins, transformCompositeTags(compositeTags), ErrorHandler.noop)
                    compositeTags
                }
            }

            is TextOperandVersion1 -> handleUnexpectedTextOperand()
            null -> handleUnexpectedNullOperand()
        }
        return compositeTagsList
    }

    private fun createCompositeTags(
        operand: TagOperandVersioned,
        twins: Iterable<Tag.Mutable>,
        prefix: CollectedText,
        suffix: CollectedText,
    ): List<CompositeTag> =
        twins.map { twin ->
            val name = prefix.text + twin.name + suffix.text
            val suggestMinimumLength = prefix.suggestMinimumLength + coerceSuggestMinimumLength(operand, twin) + suffix.suggestMinimumLength
            val latest = TagVersion1(
                name = FilterFileSystem.tagPrefixString + name,
                suggestMinimumLength = suggestMinimumLength
            )
            CompositeTag(
                suggestMinimumLength,
                registerTagHelper.getOrCreateTag(registry, name, operation.configFile.path, Tag.Parameter.cannotRename, latest = latest)
            )
        }

    private fun linkTags(
        current: TagOperandVersioned,
        parents: Iterable<Tag.Mutable>,
        twins: Iterable<Tag.Mutable>,
        tags: Iterable<Tag.Mutable>,
        twinParentErrorHandler: ErrorHandler,
    ) {
        LinkTagHelper.linkTwins(tags.iterator())
        LinkTagHelper.linkParents(operation.configFile.path, tags, parents, true, ctx)
        if (current.parent == true) {
            LinkTagHelper.linkParents(operation.configFile.path, tags, twins, true, twinParentErrorHandler)
        }
    }

    private fun coerceSuggestMinimumLength(operand: TagOperandVersioned, tag: Tag): Int =
        if (operand.suggestMinimumLength != null) {
            applicationCtx.filter.coerceSuggestMinimumLength(tag.name, operand.suggestMinimumLength)
        } else if (operand == lastTagOperand) {
            tag.suggestMinimumLength
        } else {
            applicationCtx.filter.coerceSuggestMinimumLength(tag.name, applicationCtx.filter.operandSuggestMinimumLength)
        }

    private fun handleUnexpectedNullOperand() =
        handleIllegalState("Unexpected null operand.", Unit)

    private fun handleUnexpectedTextOperand() =
        handleIllegalState("Unexpected text operand.", Unit)

    private fun <T> handleIllegalState(message: String, value: T): T {
        val fullMessage = "${operation.configFile.path.absoluteString}: Illegal state: $message"
        logger.error(fullMessage)
        ctx.onError(fullMessage)
        return value
    }

    private fun getAffectedTags(operand: TagOperandVersioned): Sequence<Tag.Mutable> {
        val tags = TagNameHelper.getSequenceOfAliases(ctx, operation.configFile.path, operand.name, TagNameHelper.nameResolverWithoutValidation)
            .map { registry.getOrCreateTag(it, Tag.Parameter.standard, ctx) }
        return CommonUtil.prependNullable(tags.takeIf { operand.self == true }, when (operand) {
            is ChildrenOperandVersion1 -> tags.flatMap { it.getSequenceOfChildren(Tag.ChildrenParameter.notComputed) }
            is DescendantOperandVersion1 -> tags.flatMap { it.getSequenceOfDescendants(Tag.ChildrenParameter.notComputed) }
            is LeafOperandVersion1 -> tags.flatMap { it.getSequenceOfLeafs(Tag.ChildrenParameter.notComputed) }
            is SelfOperandVersion1 -> sequenceOf()
        })
    }

    private fun findLastTagOperand(): TagOperandVersioned? {
        for (operand in operation.latest.operands.reversed()) {
            if (operand is TagOperandVersioned) {
                return operand
            }
        }
        return null
    }

    companion object {
        private fun transformCompositeTags(compositeTags: Iterable<CompositeTag>): Iterable<Tag.Mutable> =
            compositeTags.asSequence().map { it.tag }.asIterable()
    }
}