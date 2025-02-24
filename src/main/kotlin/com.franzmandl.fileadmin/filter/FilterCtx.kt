package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.EnumCollection
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.IteratorUtil
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.dto.config.TimeBuilder
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode0
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.PathCondition
import com.franzmandl.fileadmin.vfs.SafePath
import java.time.Period
import java.util.*
import java.util.regex.PatternSyntaxException

class FilterCtx(
    val registry: TagRegistry.Phase2,
    private val inputs: List<Input>,
    val output: SafePath,
    @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
    private val autoIntersect: Boolean,
    private val hierarchicalTags: Boolean,
    private val rootTagsMinPriority: Int?,
    private val scanModes: EnumCollection<CommandId, ScanMode>,
) {
    private var isDirty = true

    private fun getLockedItemsList(ctx: RequestCtx, scanMode: ScanMode, errorHandler: ErrorHandler): LockedItemsList {
        isDirty = isDirty || scanMode.markDirty
        if (!scanMode.enabled) {
            return LockedItemsList(listOf())
        }
        return if (isDirty || !scanMode.onlyIfDirty) {
            isDirty = false
            registry.clearUnknownIf(scanMode.clearUnknown)
            LockedItemsList(inputs.map { it.getItemsLocked(ctx, registry, scanMode, errorHandler) })
        } else {
            LockedItemsList(listOf())
        }
    }

    fun scanItems(ctx: RequestCtx, commandId: CommandId, errorHandler: ErrorHandler, quickBypass: (() -> Unit)? = null) {
        val scanMode = scanModes[commandId]
        if (scanMode.quickBypass && quickBypass != null) {
            quickBypass()
        } else {
            getLockedItemsList(ctx, scanMode, errorHandler)
        }
    }

    fun getLockedItemsList(ctx: RequestCtx, commandId: CommandId, errorHandler: ErrorHandler): LockedItemsList =
        getLockedItemsList(ctx, scanModes[commandId], errorHandler)

    fun getItem(path: SafePath): Item? {
        for (input in inputs) {
            val item = input.getItemLocked(path)
            if (item != null) {
                return item
            }
        }
        return null
    }

    fun addItem(ctx: RequestCtx, inode: Inode1<*>, errorHandler: ErrorHandler) {
        val ancestors = Inode0.getAncestorMap(inode.inode0)
        for (input in inputs) {
            if (!inode.inode0.path.startsWith(input.path)) {
                continue
            }
            val timeBuilder = TimeBuilder()
            if (input.condition.evaluate(input.path, ancestors, inode.inode0.path.sliceParts(input.path), errorHandler, timeBuilder)) {
                input.addItemLocked(ctx, PathCondition.ExternalInode.Impl(null, inode, null, timeBuilder.build()), registry, errorHandler)
            }
        }
    }

    fun isTimeDirectory(path: SafePath): Boolean {
        for (input in inputs) {
            if (input.isTimeDirectoryLocked(path)) {
                return true
            }
        }
        return false
    }

    fun deleteItem(path: SafePath) {
        for (input in inputs) {
            input.deleteItemLocked(path)
        }
    }

    fun filterItems(requestCtx: RequestCtx, items: MutableList<Item>, filters: List<Filter>, errorHandler: ErrorHandler): MutableList<Item> {
        for (filter in filters) {
            val iterator = items.iterator()
            while (iterator.hasNext()) {
                if (!filter.isKept(requestCtx, this, iterator.next(), errorHandler)) {
                    iterator.remove()
                }
            }
        }
        return items
    }

    fun filterNames(items: List<Item>, filters: List<Filter>): FilterNamesResult {
        val tags = mutableSetOf<Tag>()
        val commonTags = mutableSetOf<Tag>()
        items.firstOrNull()?.let { commonTags += it.allTags.all }
        for (item in items) {
            commonTags.retainAll(item.allTags.all)
            tags += item.allTags.all
        }
        // tagsInFilter is not always a subset of commonTags. If items is empty, then commonTags is empty, but tagsInFilter might contain tags.
        val tagsInFilter = mutableSetOf<Tag>()
        for (filter in filters) {
            if (filter is TagFilter) {
                tagsInFilter += filter.tag.getSequenceOfAncestors()  // Ancestors are implied.
                tagsInFilter += filter.tag
            }
        }
        if (!(registry.systemTags.input in tagsInFilter || registry.systemTags.input.getSequenceOfDescendants(Tag.ChildrenParameter.all)
                .any { it in tagsInFilter } || registry.systemTags.input.getSequenceOfDescendants(Tag.ChildrenParameter.all)
                .any { it in commonTags })
        ) {
            commonTags.remove(registry.systemTags.input)
        }
        tags.removeAll(commonTags)
        if (rootTagsMinPriority != null && filters.isEmpty()) {
            tags.retainAll { tag -> tag.parameter.priority >= rootTagsMinPriority }
        }
        if (hierarchicalTags) {
            tags.retainAll { tag ->
                tag.parameter.isRoot || Tag.getSequenceOfAllParents(tag).any { parent ->
                    parent in tagsInFilter || parent in commonTags || parent.parents.any { grandparent ->
                        grandparent.getInheritChildrenOfTags().any { childrenOfTag ->
                            childrenOfTag.parents.any { it in tagsInFilter }
                        }
                    }
                }
            }
        }
        val lastTagInFilterChildren = mutableSetOf<Tag>()
        tagsInFilter.lastOrNull()?.let {
            lastTagInFilterChildren += it.getSequenceOfChildren(Tag.ChildrenParameter.all)
        }
        return FilterNamesResult(
            commonTags,
            tags.mapTo(mutableSetOf()) { tag -> CommonUtil.takeStringIf(tag in lastTagInFilterChildren, FilterFileSystem.childPrefixString) + tag.friendlyName })
    }

    class FilterNamesResult(
        val commonTags: Set<Tag>,
        val names: MutableSet<String>,
    )

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
        private val errorHandler: ErrorHandler,
        private val registry: TagRegistry,
    ) {
        private val mutableFilters = mutableListOf<Filter>()
        val filters: List<Filter> = mutableFilters
        var lastTag: Tag? = null
        var not = false
        var reason = TagFilter.Reason.Any
        var relationship = TagFilter.Relationship.Any

        fun addElseFilter() {
            lastTag?.let { addFilter(ElseFilter(it)) } ?: errorHandler.onError("Else operator only possible after tag.")
        }

        fun addTagFilter(name: String) {
            val tag = registry.getTag(name)
            if (tag == null) {
                errorHandler.onError("Tag not found: $name")
            } else {
                addFilter(TagFilter(tag, reason, relationship))
                lastTag = tag
                reason = TagFilter.Reason.Any
                relationship = TagFilter.Relationship.Any
            }
        }

        fun addFilter(filter: Filter) {
            mutableFilters += if (not) NotFilter(filter) else filter
            not = false
        }
    }

    fun filter(ctx: RequestCtx, path: SafePath, filterStrings: List<String>, errorHandler: ErrorHandler): FilterResult {
        var isOperator = false
        val builder = FiltersBuilder(errorHandler, registry)
        val iterator = FilterStringsIterator(filterStrings.iterator())
        var intersect = autoIntersect || !iterator.hasNext()
        var expensiveOperation = false
        while (iterator.hasNext()) {
            val value = iterator.next()
            if (isOperator) {
                isOperator = false
                when (value) {
                    FilterFileSystem.operator -> {
                        isOperator = true
                        if (!iterator.hasNext()) {
                            val children = if (builder.lastTag != null) FilterFileSystem.operatorsWithElse else FilterFileSystem.operatorsWithoutElse
                            return createFilterResult(resolveChildSet(path, children), builder.filters, filterStrings)
                        }
                    }

                    FilterFileSystem.operatorEvaluate -> {
                        if (!iterator.hasNext()) {
                            val childSet = EvaluateChildSet(expensiveOperation, this, builder.filters, path, ctx, errorHandler)
                            return createFilterResult(childSet, builder.filters, filterStrings)
                        }
                    }

                    FilterFileSystem.operatorElse -> builder.addElseFilter()

                    FilterFileSystem.operatorIntersect -> {
                        if (!iterator.hasNext()) {
                            intersect = true
                        }
                    }

                    FilterFileSystem.operatorMax -> {
                        if (!iterator.hasNext()) {
                            return createFilterResult(resolveChildSet(path, FilterFileSystem.operatorMaxSuggestions), builder.filters, filterStrings)
                        }
                        builder.addFilter(
                            createMaxFilter(iterator, errorHandler)
                                ?: return createFilterResult(resolveOperatorEndString(path), builder.filters, filterStrings)
                        )
                    }

                    FilterFileSystem.operatorNot -> builder.not = true

                    FilterFileSystem.operatorTagReason -> {
                        if (!iterator.hasNext()) {
                            return createFilterResult(resolveChildSet(path, FilterFileSystem.operatorTagReasons.keys), builder.filters, filterStrings)
                        }
                        val rawReason = iterator.next()
                        val reason = FilterFileSystem.operatorTagReasons[rawReason]
                        if (reason == null) {
                            errorHandler.onError("Illegal tag reason: $rawReason")
                            return createFilterResult(FilterChildSet.Simple.empty, builder.filters, filterStrings)
                        }
                        builder.reason = reason
                    }

                    FilterFileSystem.operatorTagRelationship -> {
                        if (!iterator.hasNext()) {
                            return createFilterResult(resolveChildSet(path, FilterFileSystem.operatorTagRelationships.keys), builder.filters, filterStrings)
                        }
                        val rawRelationship = iterator.next()
                        val relationship = FilterFileSystem.operatorTagRelationships[rawRelationship]
                        if (relationship == null) {
                            errorHandler.onError("Illegal tag relationship: $rawRelationship")
                            return createFilterResult(FilterChildSet.Simple.empty, builder.filters, filterStrings)
                        }
                        builder.relationship = relationship
                    }

                    FilterFileSystem.operatorText -> {
                        if (!iterator.hasNext()) {
                            return createFilterResult(resolveChildSet(path, FilterFileSystem.operatorTextReasons.keys), builder.filters, filterStrings)
                        }
                        val rawReason = iterator.next()
                        val reason = FilterFileSystem.operatorTextReasons[rawReason]
                        if (reason == null) {
                            errorHandler.onError("Illegal text reason: $rawReason")
                            return createFilterResult(FilterChildSet.Simple.empty, builder.filters, filterStrings)
                        }
                        val argumentBuilder = StringBuilder()
                        if (!createStringArgument(iterator, argumentBuilder)) {
                            return createFilterResult(resolveOperatorEndString(path), builder.filters, filterStrings)
                        }
                        val argument = argumentBuilder.toString()
                        builder.addFilter(
                            TextFilter(
                                reason, try {
                                    Regex(argument, RegexOption.IGNORE_CASE)
                                } catch (e: PatternSyntaxException) {
                                    errorHandler.onError("""Illegal regex "$argument": ${e.message}""")
                                    return createFilterResult(FilterChildSet.Simple.empty, builder.filters, filterStrings)
                                }
                            )
                        )
                        expensiveOperation = expensiveOperation || reason == TextFilter.Reason.Content || reason == TextFilter.Reason.ContentOrName
                    }

                    FilterFileSystem.operatorTime -> {
                        if (!iterator.hasNext()) {
                            return createFilterResult(resolveChildSet(path, FilterFileSystem.compareOperators.keys), builder.filters, filterStrings)
                        }
                        val operator = getCompareOperator(iterator.next(), errorHandler)
                            ?: return createFilterResult(FilterChildSet.Simple.empty, builder.filters, filterStrings)
                        if (!iterator.hasNext()) {
                            return createFilterResult(
                                resolveChildSet(
                                    path, listOf(
                                        "0000-00-00",
                                        "${ctx.now - Period.ofWeeks(2)} - 2 weeks ago",
                                        "${ctx.now} - now",
                                        "9999-99-99",
                                    )
                                ), builder.filters, filterStrings
                            )
                        }
                        val rawArgument = iterator.next()
                        val argument = CommonUtil.parseDate(rawArgument)
                        if (argument == null) {
                            errorHandler.onError("Illegal date: $rawArgument")
                            return createFilterResult(FilterChildSet.Simple.empty, builder.filters, filterStrings)
                        }
                        builder.addFilter(TimeFilter(operator, argument))
                    }

                    else -> errorHandler.onError("Illegal operator: $value")
                }
            } else if (value.isEmpty()) {
                isOperator = true
            } else {
                builder.addTagFilter(FilterFileSystem.trimName(value))
            }
            isOperator = isOperator || iterator.parts.hasNext()
        }
        val childSet = EvaluateChildSet(expensiveOperation, this, builder.filters, path, ctx, errorHandler)
        return if (intersect) {
            FilterResult(
                canRename(builder.filters.lastOrNull()),
                IntersectChildSet(childSet, this, builder.filters, builder.lastTag, path, ctx),
                this, builder.filters, filterStrings,
            )
        } else {
            createFilterResult(CustomChildSet(resolveChildren(path, FilterFileSystem.operatorsWithPrefix), childSet), builder.filters, filterStrings)
        }
    }

    private fun resolveChildren(path: SafePath, names: Iterable<String>): Set<SafePath> =
        names.mapTo(mutableSetOf()) { path.resolve(it) }

    private fun resolveChildSet(path: SafePath, names: Iterable<String>): FilterChildSet.Simple =
        FilterChildSet.Simple.createSameSize(resolveChildren(path, names))

    private fun resolveOperatorEndString(path: SafePath): FilterChildSet.Simple =
        resolveChildSet(path, listOf(FilterFileSystem.operatorEndString))

    private fun createFilterResult(childSet: FilterChildSet, filters: List<Filter>, filterStrings: List<String>): FilterResult =
        FilterResult(false, childSet, this, filters, filterStrings)

    private fun createMaxFilter(iterator: Iterator<String>, errorHandler: ErrorHandler): MaxFilter? {
        val string = iterator.next()
        val integer = string.toIntOrNull()
        return if (integer == null) {
            errorHandler.onError("Invalid number: $string")
            null
        } else if (iterator.hasNext()) {
            val terminal = iterator.next()
            if (terminal == FilterFileSystem.operatorEndString) {
                MaxFilter(integer)
            } else {
                errorHandler.onError("Syntax error: expected ${FilterFileSystem.operatorEndString}")
                null
            }
        } else null
    }

    private fun getCompareOperator(value: String, errorHandler: ErrorHandler): CompareOperator? =
        FilterFileSystem.compareOperators[value] ?: errorHandler.onError("Illegal compare operator: $value")

    private fun createStringArgument(iterator: Iterator<String>, builder: StringBuilder): Boolean {
        var first = true
        return IteratorUtil.consumeUntil(iterator, FilterFileSystem.operatorEndString) {
            if (first) {
                first = false
            } else {
                builder.append('/')
            }
            builder.append(it)
        }
    }

    class CustomChildSet(
        override val children: Set<SafePath>,
        private val childSet: FilterChildSet,
    ) : FilterChildSet by childSet

    class EvaluateChildSet(
        private val expensiveOperation: Boolean,
        private val filterCtx: FilterCtx,
        private val filters: List<Filter>,
        private val path: SafePath,
        private val requestCtx: RequestCtx,
        private val errorHandler: ErrorHandler,
    ) : FilterChildSet {
        override val items: List<Item> by lazy {
            filterCtx.filterItems(
                requestCtx,
                filterCtx.getLockedItemsList(requestCtx, CommandId.FilterItems, errorHandler).toMutableList(),
                filters, errorHandler,
            )
        }
        override val children: Set<SafePath> get() = items.mapTo(mutableSetOf(path.resolve(requestCtx.application.system.directoryName))) { it.inode.inode0.path }
        override val estimatedSize: Int? get() = if (expensiveOperation) null else items.size
        override val size: Int get() = items.size
    }

    class IntersectChildSet(
        private val childSet: EvaluateChildSet,
        private val filterCtx: FilterCtx,
        private val filters: List<Filter>,
        private val lastTag: Tag?,
        private val path: SafePath,
        private val requestCtx: RequestCtx,
    ) : FilterChildSet by childSet {
        override val children: Set<SafePath>
            get() {
                val items = childSet.items
                val children = mutableSetOf<SafePath>()
                filterCtx.filterNames(items, filters).names.mapTo(children) { name -> path.resolve(name) }
                if (children.isEmpty() && filters.isNotEmpty()) {
                    items.mapTo(children) { it.inode.inode0.path }
                } else {
                    children += path.resolve(FilterFileSystem.operatorPrefix + FilterFileSystem.operator)
                    children += path.resolve(FilterFileSystem.operatorPrefix + FilterFileSystem.operatorEvaluate)
                    if (lastTag != null) {
                        children += path.resolve(FilterFileSystem.operatorPrefix + FilterFileSystem.operatorElse)
                    }
                }
                children += path.resolve(requestCtx.application.system.directoryName)
                return children
            }
    }

    private fun canRename(filter: Filter?): Boolean =
        filter is TagFilter && filter.tag.parameter.canRename || filter is NotFilter && canRename(filter.filter)
}