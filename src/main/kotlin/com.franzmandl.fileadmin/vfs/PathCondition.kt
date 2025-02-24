package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.IteratorUtil
import com.franzmandl.fileadmin.dto.config.PathConditionVersioned
import com.franzmandl.fileadmin.dto.config.TimeBuilder
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.time.LocalDate
import java.util.*

class PathCondition(
    val components: List<Component>,
    val finalComponent: Component?,
    val original: PathConditionVersioned,
    val pruneNames: Set<String>,
    val pruneContentRegex: Regex?,
    val rootPatterns: Patterns,
    val yieldRoot: Boolean,
) {
    class Component(
        val enterNames: Set<String>,
        val ignoreNonDirectories: Boolean,
        val index: Int,
        val patterns: Patterns,
        val pruneNames: Set<String>,
        val time: Boolean,
        val yield: Boolean,
    )

    class Pattern(
        private val nameGlobs: List<PathMatcher>?,
        private val nameRegex: Regex?,
        private val pathRegex: Regex?,
        private val whitelist: Set<String>?,
    ) {
        constructor(
            nameGlobString: String?,
            nameRegexString: String?,
            pathRegexString: String?,
            whitelist: Set<String>?,
        ) : this(
            nameGlobString?.split(orSeparator)?.map { CommonUtil.createGlob(it) },
            nameRegexString?.let { Regex(it) },
            pathRegexString?.let { Regex(it) },
            whitelist,
        )

        fun evaluateOptimistic(path: SafePath): Boolean =
            evaluateWhitelist(path) && (nameGlobs?.let { CommonUtil.evaluateGlobs(it, Path.of(path.name)) }.takeIf { it == false }
                ?: nameRegex?.containsMatchIn(path.name).takeIf { it == false }
                ?: pathRegex?.containsMatchIn(path.absoluteString) ?: true)

        fun evaluatePessimistic(path: SafePath): Boolean =
            evaluateWhitelist(path) && (nameGlobs?.let { CommonUtil.evaluateGlobs(it, Path.of(path.name)) } ?: false
                    || nameRegex?.containsMatchIn(path.name) ?: false
                    || pathRegex?.containsMatchIn(path.absoluteString) ?: false)

        private fun evaluateWhitelist(path: SafePath): Boolean =
            whitelist?.contains(path.name) != false

        fun copyWithoutWhitelist(): Pattern =
            if (whitelist != null) Pattern(nameGlobs, nameRegex, pathRegex, null) else this

        companion object {
            private const val orSeparator = '|'
        }
    }

    class Patterns(
        val common: Pattern,
        val directory: Pattern,
        val file: Pattern,
    ) {
        fun copyWithoutWhitelist(): Patterns {
            val common = this.common.copyWithoutWhitelist()
            val directory = this.directory.copyWithoutWhitelist()
            val file = this.file.copyWithoutWhitelist()
            return if (common !== this.common || directory !== this.directory || file !== this.file) Patterns(common, directory, file) else this
        }
    }

    fun evaluate(parent: SafePath, ancestors: Map<SafePath, Inode0>, parts: List<String>, errorHandler: ErrorHandler, timeBuilder: TimeBuilder? = null): Boolean {
        if (!evaluatePatterns(parent, ancestors[parent], rootPatterns)) {
            return false
        }
        val partsIterator = TransparentIterator.create(parts) ?: return yieldRoot
        val componentsIterator = components.iterator()
        var mutableComponent = IteratorUtil.nextOrNull(componentsIterator)
        while (mutableComponent != null) {
            val component = mutableComponent
            if (isPruned(component, partsIterator.current)) {
                return false
            }
            if (partsIterator.current in component.enterNames) {
                partsIterator.nextOrNull() ?: return false
                continue
            }
            if (component.time && !evaluateTime(component, partsIterator, errorHandler, timeBuilder)) {
                return false
            }
            val path = parent.resolve(partsIterator.processed)
            if (!evaluatePatterns(path, ancestors, component.patterns)) {
                return false
            }
            partsIterator.nextOrNull() ?: return component.yield
            if (isContentPruned(path.name)) {
                return false
            }
            mutableComponent = IteratorUtil.nextOrNull(componentsIterator)
        }
        return evaluateFinalComponent(parent, ancestors, partsIterator, errorHandler, timeBuilder)
    }

    private fun evaluateFinalComponent(
        parent: SafePath,
        ancestors: Map<SafePath, Inode0>,
        partsIterator: TransparentIterator<String>,
        errorHandler: ErrorHandler,
        timeBuilder: TimeBuilder?,
    ): Boolean {
        if (finalComponent == null) {
            return false
        }
        while (true) {
            if (isPruned(finalComponent, partsIterator.current)) {
                return false
            }
            if (partsIterator.current in finalComponent.enterNames) {
                partsIterator.nextOrNull() ?: return false
                continue
            }
            if (finalComponent.time && !evaluateTime(finalComponent, partsIterator, errorHandler, timeBuilder)) {
                return false
            }
            partsIterator.nextOrNull() ?: break
        }
        return evaluatePatterns(parent.resolve(partsIterator.processed), ancestors, finalComponent.patterns)
    }

    private fun evaluatePatterns(path: SafePath, ancestors: Map<SafePath, Inode0>, patterns: Patterns): Boolean =
        evaluatePatterns(path, ancestors[path], patterns)

    private fun evaluatePatterns(path: SafePath, inode: Inode0?, patterns: Patterns): Boolean {
        if (!patterns.common.evaluateOptimistic(path)) {
            return false
        }
        if (inode != null) {
            if (!patterns.directory.evaluateOptimistic(inode.path) && inode.contentOperation.canDirectoryGet) {
                return false
            }
            if (!patterns.file.evaluateOptimistic(inode.path) && inode.contentOperation.canFileGet) {
                return false
            }
        }
        return true
    }

    private fun evaluateTime(
        component: Component,
        partsIterator: TransparentIterator<String>,
        errorHandler: ErrorHandler,
        timeBuilder: TimeBuilder?,
    ): Boolean {
        val yearToMonthToDay = CommonUtil.parseDatePairs(partsIterator.current)
        val (year, monthToDay) = if (yearToMonthToDay != null) yearToMonthToDay else {
            errorHandler.onError("""Path "${partsIterator.current}" does not have a time.""")
            return true
        }
        timeBuilder?.setYear(year)
        val (month, day) = monthToDay ?: return evaluateTime(component, partsIterator.selfIfNextNotNull() ?: return false, year, errorHandler, timeBuilder)
        timeBuilder?.setYearMonth(year, month)
        return if (day == null) {
            evaluateTime(component, partsIterator.nextOrNull() ?: return false, year, month, errorHandler, timeBuilder)
        } else {
            timeBuilder?.setYearMonthDay(year, month, day)
            true
        }
    }

    private fun evaluateTime(
        component: Component,
        partsIterator: TransparentIterator<String>,
        expectedYear: String,
        errorHandler: ErrorHandler,
        timeBuilder: TimeBuilder?,
    ): Boolean {
        if (isPruned(component, partsIterator.current)) {
            return false
        }
        val yearToMonthToDay = CommonUtil.parseDatePairs(partsIterator.current)
        val (year, monthToDay) = if (yearToMonthToDay != null) yearToMonthToDay else {
            errorHandler.onError("""Path "${partsIterator.current}" does not have a time.""")
            return true
        }
        if (year != expectedYear) {
            timeBuilder?.setYear(year)
            errorHandler.onError("""Path "${partsIterator.current}" does not belong into year $expectedYear.""")
        }
        val (month, day) = if (monthToDay != null) monthToDay else {
            errorHandler.onError("""Path "${partsIterator.current}" must at least specify a month.""")
            return true
        }
        timeBuilder?.setYearMonth(year, month)
        return if (day == null) {
            evaluateTime(component, partsIterator.nextOrNull() ?: return false, year, month, errorHandler, timeBuilder)
        } else {
            timeBuilder?.setYearMonthDay(year, month, day)
            true
        }
    }

    private fun evaluateTime(
        component: Component,
        part: String,
        expectedYear: String,
        expectedMonth: String,
        errorHandler: ErrorHandler,
        timeBuilder: TimeBuilder?,
    ): Boolean {
        if (isPruned(component, part)) {
            return false
        }
        val yearToMonthToDay = CommonUtil.parseDatePairs(part)
        val (year, monthToDay) = if (yearToMonthToDay != null) yearToMonthToDay else {
            errorHandler.onError("""Path "$part" does not have a time.""")
            return true
        }
        val belongingErrorHandler = if (year != expectedYear) {
            timeBuilder?.setYear(year)
            errorHandler.onError("""Path "$part" does not belong into year $expectedYear.""")
        } else errorHandler
        val (month, day) = if (monthToDay != null) monthToDay else {
            errorHandler.onError("""Path "$part" must specify a month and day.""")
            return true
        }
        if (month != expectedMonth) {
            timeBuilder?.setYearMonth(year, month)
            belongingErrorHandler?.onError("""Path "$part" does not belong into month $expectedMonth.""")
        }
        if (day == null) {
            errorHandler.onError("""Path "$part" must specify a day.""")
        } else {
            timeBuilder?.setYearMonthDay(year, month, day)
        }
        return true
    }

    private class TransparentIterator<T>(
        private val iterator: Iterator<T>,
        var current: T,
    ) : Iterator<T> {
        val processed = LinkedList<T>().apply { add(current) }

        override fun hasNext(): Boolean =
            iterator.hasNext()

        override fun next(): T {
            current = iterator.next()
            processed += current
            return current
        }

        fun nextOrNull(): T? =
            IteratorUtil.nextOrNull(this)

        fun selfIfNextNotNull(): TransparentIterator<T>? =
            if (nextOrNull() == null) null else this

        companion object {
            fun <T> create(iterable: Iterable<T>): TransparentIterator<T>? {
                val iterator = iterable.iterator()
                return TransparentIterator(iterator, IteratorUtil.nextOrNull(iterator) ?: return null)
            }
        }
    }

    sealed interface VisitedInode<P> {
        val component: Component?
        val inode1: Inode1<*>
    }

    sealed interface InternalInode<P> : VisitedInode<P> {
        class Impl<P>(
            override val component: Component?,
            override val inode1: Inode1<*>,
        ) : InternalInode<P>
    }

    sealed interface ExternalInode<P> : VisitedInode<P> {
        val payload: P
        val time: LocalDate?

        class Impl<P>(
            override val component: Component?,
            override val inode1: Inode1<*>,
            override val payload: P,
            override val time: LocalDate?,
        ) : ExternalInode<P>
    }

    sealed interface LeafInode<P> : ExternalInode<P> {
        class Impl<P>(
            override val component: Component?,
            override val inode1: Inode1<*>,
            override val payload: P,
            override val time: LocalDate?,
        ) : LeafInode<P>
    }

    class Parameter<P>(
        @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
        val createPayload: (P, Inode1<*>, Component) -> P,
        val evaluatePatterns: Boolean = true,
        val errorHandler: ErrorHandler,
        val onTimeDirectory: (Inode1<*>) -> Unit = {},
        val rootPayload: P,
        val yieldInternal: Boolean = false,
    ) {
        fun withEvaluatePatterns(evaluatePatterns: Boolean) = Parameter(
            createPayload = createPayload,
            evaluatePatterns = evaluatePatterns,
            errorHandler = errorHandler,
            rootPayload = rootPayload,
            yieldInternal = yieldInternal,
        )

        companion object {
            val createNullPayload: (Nothing?, Inode1<*>, Component) -> Nothing? = { _, _, _ -> null }
        }
    }

    fun <P> getSequenceOfDescendants(
        ctx: PathFinder.Ctx,
        directory: Inode1<*>,
        parameter: Parameter<P>,
    ): Sequence<VisitedInode<P>> {
        if (parameter.evaluatePatterns && !evaluatePatterns(directory.inode0.path, directory.inode0, rootPatterns)) {
            return sequenceOf()
        }
        val sequence = getSequenceOfDescendantsHelper(ctx, directory, 0, parameter, parameter.rootPayload, null)
        return when {
            yieldRoot -> sequenceOf(ExternalInode.Impl(null, directory, parameter.rootPayload, null)) + sequence
            parameter.yieldInternal -> sequenceOf(InternalInode.Impl<P>(null, directory)) + sequence
            else -> sequence
        }
    }

    private fun <P> getSequenceOfDescendantsHelper(
        ctx: PathFinder.Ctx,
        directory: Inode1<*>,
        componentIndex: Int,
        parameter: Parameter<P>,
        parentPayload: P,
        time: LocalDate?,
    ): Sequence<VisitedInode<P>> =
        when {
            componentIndex < components.size -> getSequenceOfDescendantsHelper(
                ctx, directory,
                components[componentIndex],
                componentIndex + 1,
                parameter, parentPayload, time,
            )

            finalComponent != null -> sequence {
                for (inode in getSequenceOfDescendantsHelper(ctx, directory, finalComponent, componentIndex, parameter.withEvaluatePatterns(false), parentPayload, time)) {
                    try {
                        if (evaluateFinalComponentPatterns(inode.inode1.inode0, finalComponent)) {
                            yield(inode)
                        }
                    } catch (e: HttpException) {
                        parameter.errorHandler.onError(e.message)
                    }
                }
            }

            else -> sequenceOf()
        }

    private fun evaluateFinalComponentPatterns(inode: Inode0, component: Component): Boolean {
        val commonResult = component.patterns.common.evaluatePessimistic(inode.path)
        if ((commonResult || component.patterns.file.evaluatePessimistic(inode.path)) && inode.contentOperation.canFileGet) {
            return true
        }
        if ((commonResult || component.patterns.directory.evaluatePessimistic(inode.path)) && inode.contentOperation.canDirectoryGet) {
            return true
        }
        return false
    }

    private fun <P> getSequenceOfDescendantsHelper(
        ctx: PathFinder.Ctx,
        directory: Inode1<*>,
        component: Component,
        nextComponentIndex: Int,
        parameter: Parameter<P>,
        parentPayload: P,
        time: LocalDate?,
    ): Sequence<VisitedInode<P>> =
        if (directory.inode0.contentOperation.canDirectoryGet) {
            sequence {
                val sequence = when {
                    component.time -> visitTime(ctx, component, directory, parameter, parentPayload, time)
                    else -> visitDepth(ctx, directory, 1, 1, component, parameter, parentPayload, time)
                }
                for (inode in sequence) {
                    when (inode) {
                        is InternalInode -> yield(inode)
                        is ExternalInode -> {
                            if (component.yield) {
                                yield(inode)
                            } else if (parameter.yieldInternal) {
                                yield(InternalInode.Impl(component, inode.inode1))
                            }
                            if (inode is LeafInode) {
                                yieldAll(
                                    getSequenceOfDescendantsHelper(
                                        ctx, inode.inode1, nextComponentIndex, parameter,
                                        parameter.createPayload(parentPayload, inode.inode1, component),
                                        time,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (!component.ignoreNonDirectories) {
                parameter.errorHandler.onError("""Path "${directory.inode0.path.absoluteString}" is not a directory.""")
            }
            sequenceOf()
        }

    /**
     * depth = 0 ... itself
     */
    private fun <P> visitDepth(
        ctx: PathFinder.Ctx,
        directory: Inode1<*>,
        minDepth: Int,
        maxDepth: Int,
        component: Component,
        parameter: Parameter<P>,
        parentPayload: P,
        time: LocalDate?,
    ): Sequence<VisitedInode<P>> =
        if (isContentPruned(directory.inode0.path.name)) sequenceOf() else sequence {
            for (child in directory.children) {
                if (isPruned(component, child.name)) {
                    continue
                }
                val inode = ctx.createPathFinder(child).find()
                if (child.name in component.enterNames) {
                    yieldAll(visitDepth(ctx, inode, minDepth, maxDepth, component, parameter, parentPayload, time))
                    continue
                }
                if (parameter.evaluatePatterns && !evaluatePatterns(child, inode.inode0, component.patterns)) {
                    continue
                }
                if (maxDepth == 1) {
                    yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), time))
                } else {
                    if (minDepth <= 1) {
                        yield(ExternalInode.Impl(component, directory, parameter.createPayload(parentPayload, inode, component), time))
                    } else if (parameter.yieldInternal) {
                        yield(InternalInode.Impl(component, directory))
                    }
                    try {
                        yieldAll(
                            visitDepth(
                                ctx,
                                inode,
                                minDepth - 1,
                                maxDepth - 1,
                                component,
                                parameter,
                                parameter.createPayload(parentPayload, inode, component),
                                time,
                            )
                        )
                    } catch (e: HttpException) {
                        parameter.errorHandler.onError(e.message)
                    }
                }
            }
        }

    private fun <P> visitTime(
        ctx: PathFinder.Ctx,
        component: Component,
        directory: Inode1<*>,
        parameter: Parameter<P>,
        parentPayload: P,
        time: LocalDate?,
    ): Sequence<VisitedInode<P>> =
        if (isContentPruned(directory.inode0.path.name)) sequenceOf() else sequence {
            parameter.onTimeDirectory(directory)
            for (child in directory.children) {
                if (isPruned(component, child.name)) {
                    continue
                }
                val inode = ctx.createPathFinder(child).find()
                if (child.name in component.enterNames) {
                    yieldAll(checkCanDirectoryGet(component, inode, visitTime(ctx, component, inode, parameter, parentPayload, time), parameter, parentPayload, time))
                    continue
                }
                if (parameter.evaluatePatterns && !evaluatePatterns(child, inode.inode0, component.patterns)) {
                    continue
                }
                val yearToMonthToDay = CommonUtil.parseDatePairs(child.name)
                val (year, monthToDay) = if (yearToMonthToDay != null) yearToMonthToDay else {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" does not have a time.""")
                    yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), null))
                    continue
                }
                val (month, day) = if (monthToDay != null) monthToDay else {
                    yieldAll(
                        checkCanDirectoryGet(
                            component, inode,
                            visitTime(ctx, component, inode, year, parameter, parentPayload),
                            parameter, parentPayload, CommonUtil.parseDate(year, null, null),
                        )
                    )
                    continue
                }
                if (day == null) {
                    yieldAll(
                        checkCanDirectoryGet(
                            component, inode,
                            visitTime(ctx, component, inode, year, month, parameter, parentPayload),
                            parameter, parentPayload, CommonUtil.parseDate(year, month, null),
                        )
                    )
                    continue
                }
                yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), CommonUtil.parseDate(year, month, day)))
            }
        }

    private fun <P> visitTime(
        ctx: PathFinder.Ctx,
        component: Component,
        directory: Inode1<*>,
        expectedYear: String,
        parameter: Parameter<P>,
        parentPayload: P,
    ): Sequence<VisitedInode<P>> =
        if (isContentPruned(directory.inode0.path.name)) sequenceOf() else sequence {
            parameter.onTimeDirectory(directory)
            for (child in directory.children) {
                if (isPruned(component, child.name)) {
                    continue
                }
                val inode = ctx.createPathFinder(child).find()
                val yearToMonthToDay = CommonUtil.parseDatePairs(child.name)
                val (year, monthToDay) = if (yearToMonthToDay != null) yearToMonthToDay else {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" does not have a time.""")
                    yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), CommonUtil.parseDate(expectedYear, null, null)))
                    continue
                }
                if (year != expectedYear) {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" does not belong into year $expectedYear.""")
                }
                val (month, day) = if (monthToDay != null) monthToDay else {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" must at least specify a month.""")
                    yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), CommonUtil.parseDate(year, null, null)))
                    continue
                }
                if (day == null) {
                    yieldAll(
                        checkCanDirectoryGet(
                            component, inode,
                            visitTime(ctx, component, inode, year, month, parameter, parentPayload),
                            parameter, parentPayload, CommonUtil.parseDate(year, month, null),
                        )
                    )
                    continue
                }
                yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), CommonUtil.parseDate(year, month, day)))
            }
        }

    private fun <P> visitTime(
        ctx: PathFinder.Ctx,
        component: Component,
        directory: Inode1<*>,
        expectedYear: String,
        expectedMonth: String,
        parameter: Parameter<P>,
        parentPayload: P,
    ): Sequence<VisitedInode<P>> =
        if (isContentPruned(directory.inode0.path.name)) sequenceOf() else sequence {
            parameter.onTimeDirectory(directory)
            for (child in directory.children) {
                if (isPruned(component, child.name)) {
                    continue
                }
                val inode = ctx.createPathFinder(child).find()
                val yearToMonthToDay = CommonUtil.parseDatePairs(child.name)
                val (year, monthToDay) = if (yearToMonthToDay != null) yearToMonthToDay else {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" does not have a time.""")
                    yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), CommonUtil.parseDate(expectedYear, expectedMonth, null)))
                    continue
                }
                val belongingErrorHandler = if (year != expectedYear) {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" does not belong into year $expectedYear.""")
                } else parameter.errorHandler
                val (month, day) = if (monthToDay != null) monthToDay else {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" must specify a month and day.""")
                    yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), CommonUtil.parseDate(year, expectedMonth, null)))
                    continue
                }
                if (month != expectedMonth) {
                    belongingErrorHandler?.onError("""Path "${child.absoluteString}" does not belong into month $expectedMonth.""")
                }
                if (day == null) {
                    parameter.errorHandler.onError("""Path "${child.absoluteString}" must specify a day.""")
                }
                yield(LeafInode.Impl(component, inode, parameter.createPayload(parentPayload, inode, component), CommonUtil.parseDate(year, month, day)))
            }
        }

    private fun <P> checkCanDirectoryGet(
        component: Component,
        inode: Inode1<*>,
        sequenceOfInodes: Sequence<VisitedInode<P>>,
        parameter: Parameter<P>,
        parentPayload: P,
        time: LocalDate?,
    ): Sequence<VisitedInode<P>> =
        if (inode.inode0.contentOperation.canDirectoryGet) {
            if (parameter.yieldInternal) sequenceOf(InternalInode.Impl<P>(component, inode)) + sequenceOfInodes else sequenceOfInodes
        } else {
            val groups = CommonUtil.dateRegex.find(inode.inode0.path.name)?.groups
            if (groups != null && groups[3]?.value == null) {
                parameter.errorHandler.onError("""Path "${inode.inode0.path.absoluteString}" must specify a day.""")
            }
            sequenceOf(LeafInode.Impl(component, inode, parentPayload, time))
        }

    private fun isPruned(component: Component, name: String): Boolean =
        name in pruneNames || name in component.pruneNames

    fun isContentPruned(name: String) =
        pruneContentRegex?.containsMatchIn(name) ?: false
}