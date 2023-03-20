package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.SafePath
import java.util.*

class FilterCtx(
    val registry: TagRegistry,
    private val inputs: List<Input>,
    val output: SafePath,
    private val hierarchicalTags: Boolean,
) {
    fun scanItems(ctx: RequestCtx, force: Boolean, onError: (String) -> Unit) {
        registry.clearIf(force)
        for (input in inputs) {
            input.getItems(ctx, registry, force, onError)
        }
    }

    fun getSequenceOfItems(ctx: RequestCtx, force: Boolean, onError: (String) -> Unit) =
        sequence {
            registry.clearIf(force)
            for (input in inputs) {
                yieldAll(input.getItems(ctx, registry, force, onError))
            }
        }

    fun filterItems(ctx: RequestCtx, filters: List<Filter>, onError: (String) -> Unit): MutableList<Item> {
        val items = getSequenceOfItems(ctx, false, onError).toMutableList()
        for (filter in filters) {
            val iterator = items.iterator()
            while (iterator.hasNext()) {
                if (!filter.isKept(this, iterator.next(), onError)) {
                    iterator.remove()
                }
            }
        }
        return items
    }

    fun filterNames(ctx: RequestCtx, onError: (String) -> Unit, result: Result): MutableSet<String> {
        val tags = mutableSetOf<Tag>()
        val items = filterItems(ctx, result.filters, onError)
        items.firstOrNull()?.let { result.commonTags.addAll(it.allTags.all) }
        for (item in items) {
            result.commonTags.retainAll(item.allTags.all)
            tags.addAll(item.allTags.all)
        }
        val tagsInFilter = mutableSetOf<Tag>()
        for (filter in result.filters) {
            if (filter is TagFilter) {
                filter.tag.addAncestorsTo(tagsInFilter)  // Ancestors are implied.
                tagsInFilter.add(filter.tag)
            }
        }
        if (!(registry.tagInput in tagsInFilter || registry.tagInput.hasAnyDescendant(tagsInFilter) || registry.tagInput.hasAnyDescendant(result.commonTags))) {
            result.commonTags.remove(registry.tagInput)
        }
        tags.removeAll(result.commonTags)
        // tagsInFilter is not always a subset of commonTags. If items is empty, then commonTags is empty, but tagsInFilter might contain tags.
        if (hierarchicalTags) {
            tags.retainAll { tag ->
                tag.parameter.isRoot || tag.parents.any { parent ->
                    parent in tagsInFilter || parent in result.commonTags || parent.parents.any { grandparent ->
                        grandparent.childrenOf.any { childrenOf ->
                            childrenOf.parents.any { it in tagsInFilter }
                        }
                    }
                }
            }
        }
        val lastTagInFilterChildren = mutableSetOf<Tag>()
        tagsInFilter.lastOrNull()?.let {
            lastTagInFilterChildren.addAll(it.children)
            for (childrenOf in it.childrenOf) {
                lastTagInFilterChildren.addAll(childrenOf.children)
            }
        }
        return tags.mapTo(mutableSetOf()) { tag -> CommonUtil.takeStringIf(tag in lastTagInFilterChildren, FilterFileSystem.childPrefixString) + tag.friendlyName }
    }

    private class FilterStringsIterator(
        val filterStrings: Iterator<String>,
    ) : Iterator<String> {
        var parts: Iterator<String> = Collections.emptyIterator()

        override fun hasNext(): Boolean =
            parts.hasNext() || filterStrings.hasNext()

        override fun next(): String =
            if (parts.hasNext()) {
                parts.next()
            } else {
                parts = filterStrings.next().split(FilterFileSystem.operatorPrefix).iterator()
                parts.next()
            }
    }

    private class FiltersBuilder(
        private val onError: (String) -> Unit,
        private val registry: TagRegistry,
    ) {
        private val mutableFilters = mutableListOf<Filter>()
        val filters: List<Filter> = mutableFilters
        var lastTag: Tag? = null
        var not = false
        var reason = TagFilter.Reason.Any
        var relationship = TagFilter.Relationship.Any

        fun addElseFilter() {
            lastTag?.let { addFilter(ElseFilter(it)) } ?: onError("Else operator only possible after tag.")
        }

        fun addTagFilter(name: String) {
            val tag = registry.getTag(name)
            if (tag == null) {
                onError("Tag not found: $name")
            } else {
                addFilter(TagFilter(tag, reason, relationship))
                lastTag = tag
                reason = TagFilter.Reason.Any
                relationship = TagFilter.Relationship.Any
            }
        }

        private fun addFilter(filter: Filter) {
            mutableFilters.add(if (not) NotFilter(filter) else filter)
            not = false
        }

        fun addIllegalAppendixError(iterator: FilterStringsIterator) {
            if (iterator.hasNext()) {
                val builder = StringBuilder("Illegal appendix: ")
                joinRemainingIteratorTo(builder, iterator.parts, FilterFileSystem.operatorPrefix)
                joinRemainingIteratorTo(builder, iterator.filterStrings, '/')
                onError(builder.toString())
            }
        }

        private fun joinRemainingIteratorTo(builder: StringBuilder, iterator: Iterator<String>, separator: Char): StringBuilder {
            while (iterator.hasNext()) {
                builder.append(separator).append(iterator.next())
            }
            return builder
        }
    }

    fun filter(ctx: RequestCtx, path: SafePath, filterStrings: List<String>, onError: (String) -> Unit): Result {
        var isOperator = false
        val builder = FiltersBuilder(onError, registry)
        val iterator = FilterStringsIterator(filterStrings.iterator())
        while (iterator.hasNext()) {
            val value = iterator.next()
            if (isOperator) {
                isOperator = false
                when (value) {
                    FilterFileSystem.operatorEvaluate -> {
                        val result = Result(false, builder.filters)
                        builder.addIllegalAppendixError(iterator)
                        filterItems(ctx, builder.filters, onError).mapTo(result.children) { it.inode.path }
                        return result
                    }

                    FilterFileSystem.operator -> {
                        isOperator = true
                        if (!iterator.hasNext()) {
                            val result = Result(false, builder.filters)
                            result.children.add(path.resolve(FilterFileSystem.operatorEvaluate))
                            result.children.add(path.resolve(FilterFileSystem.operatorNot))
                            if (builder.lastTag != null) {
                                result.children.add(path.resolve(FilterFileSystem.operatorElse))
                            }
                            FilterFileSystem.operatorReasons.mapTo(result.children) { path.resolve(it.key) }
                            FilterFileSystem.operatorRelationships.mapTo(result.children) { path.resolve(it.key) }
                            return result
                        }
                    }

                    FilterFileSystem.operatorElse -> builder.addElseFilter()
                    FilterFileSystem.operatorNot -> builder.not = true

                    else -> FilterFileSystem.operatorReasons[value]?.let { builder.reason = it }
                        ?: FilterFileSystem.operatorRelationships[value]?.let { builder.relationship = it }
                        ?: onError("Illegal operator: $value")
                }
            } else if (value.isEmpty()) {
                isOperator = true
            } else {
                builder.addTagFilter(FilterFileSystem.trimName(value))
            }
            isOperator = isOperator || iterator.parts.hasNext()
        }
        val result = Result(canRename(builder.filters.lastOrNull()), builder.filters)
        filterNames(ctx, onError, result).mapTo(result.children) { name -> path.resolve(name) }
        if (result.children.isEmpty()) {
            filterItems(ctx, builder.filters, onError).mapTo(result.children) { it.inode.path }
        } else {
            result.children.add(path.resolve(FilterFileSystem.operatorPrefix + FilterFileSystem.operator))
            result.children.add(path.resolve(FilterFileSystem.operatorPrefix + FilterFileSystem.operatorEvaluate))
            if (builder.lastTag != null) {
                result.children.add(path.resolve(FilterFileSystem.operatorPrefix + FilterFileSystem.operatorElse))
            }
        }
        return result
    }

    class Result(
        val canRename: Boolean,
        val filters: List<Filter>,
    ) {
        val children = mutableSetOf<SafePath>()
        val commonTags = mutableSetOf<Tag>()

        fun getHighlightTags(): MutableSet<Tag> {
            val highlightTags = mutableSetOf<Tag>()
            for (filter in CommonUtil.getReversedSequenceOf(filters)) {
                if (filter is TagFilter && filter.tag !in highlightTags) {
                    filter.tag.addAncestorsTo(highlightTags)
                    filter.tag.addDescendantsTo(highlightTags)
                    highlightTags.add(filter.tag)
                }
            }
            return highlightTags
        }
    }

    private fun canRename(filter: Filter?): Boolean =
        filter is TagFilter && filter.tag.parameter.canRename || filter is NotFilter && canRename(filter.filter)
}