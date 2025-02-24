package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.dto.config.TagVersion1
import com.franzmandl.fileadmin.vfs.Inode0
import com.franzmandl.fileadmin.vfs.SafePath

sealed interface Tag {
    val name: String
    val isAParents: Set<Tag>?
    val suggestMinimumLength: Int
    val suggestName: String?
    val parameter: Parameter
    val friendlyName: String
    val parents: Set<Tag>
    val comparableName: String
    val sortableName: String
    fun clearChildren(parameter: ChildrenParameter)
    fun getConfigFiles(): Map<SafePath, Inode0>
    fun getInheritChildrenOfTags(): Set<Tag>
    fun getSequenceOfChildren(parameter: ChildrenParameter): Sequence<Tag>
    fun getSequenceOfAncestors(): Sequence<Tag>
    fun getSequenceOfDescendants(parameter: ChildrenParameter): Sequence<Tag>
    fun getSequenceOfTwins(): Sequence<Tag>

    class Mutable(
        override val name: String,
        override var parameter: Parameter,
        override val suggestMinimumLength: Int,
        override var suggestName: String?,
    ) : Tag {
        private var twin: Mutable? = null
        private var computedChildren: MutableSet<Mutable>? = null
        private var definedChildren: MutableSet<Mutable>? = null
        private var inheritChildrenOfTags: MutableSet<Mutable>? = null
        override var isAParents: MutableSet<Mutable>? = null
        override fun getInheritChildrenOfTags(): Set<Tag> = inheritChildrenOfTags ?: setOf()
        override val parents = mutableSetOf<Mutable>()
        private var configFiles: MutableMap<SafePath, Inode0>? = null
        override fun getConfigFiles(): Map<SafePath, Inode0> = configFiles ?: mapOf()
        override val comparableName = FilterFileSystem.toComparableName(name)
        override val sortableName = FilterFileSystem.toSortableName(name)

        override val friendlyName: String
            get() = FilterFileSystem.priorityPrefixString.repeat(parameter.priority) + name + (twin?.let { FilterFileSystem.aliasDeliminator + it.name } ?: "")

        fun addConfigFile(inode: Inode0): Mutable {
            configFiles = (configFiles ?: mutableMapOf()).also { it[inode.path] = inode }
            return this
        }

        fun addChildrenOfTag(tag: Mutable): Mutable {
            inheritChildrenOfTags = (inheritChildrenOfTags ?: mutableSetOf()).also { it += tag }
            return this
        }

        fun addParent(parent: Mutable, isComputed: Boolean): Mutable {
            this.parents += parent
            if (isComputed) {
                parent.computedChildren = (parent.computedChildren ?: mutableSetOf()).also { it += this }
            } else {
                parent.definedChildren = (parent.definedChildren ?: mutableSetOf()).also { it += this }
            }
            return this
        }

        fun addIsAParent(isAParent: Mutable): Mutable {
            this.isAParents = (this.isAParents ?: mutableSetOf()).also { it += isAParent }
            isAParent.definedChildren = (isAParent.definedChildren ?: mutableSetOf()).also { it += this }
            return this
        }

        override fun clearChildren(parameter: ChildrenParameter) {
            if (parameter.computed) {
                computedChildren?.clear()
            }
            if (parameter.defined) {
                definedChildren?.clear()
            }
            if (parameter.inherited) {
                inheritChildrenOfTags?.clear()
            }
        }

        fun setTwin(twin: Mutable): Mutable {
            if (this.twin == null) {
                twin.twin = this
                this.twin = twin
            } else {
                // this -> this.twin -> ...
                twin.twin = this.twin
                // twin & this -> this.twin -> ...
                this.twin = twin
                // this -> twin -> this.twin -> ...
            }
            return this
        }

        override fun getSequenceOfChildren(parameter: ChildrenParameter): Sequence<Mutable> =
            sequence {
                definedChildren.takeIf { parameter.defined }?.let { yieldAll(it) }
                computedChildren.takeIf { parameter.computed }?.let { yieldAll(it) }
                inheritChildrenOfTags.takeIf { parameter.inherited }?.let {
                    for (childrenOfTag in it) {
                        yieldAll(childrenOfTag.getSequenceOfChildren(parameter))
                    }
                }
            }

        override fun getSequenceOfAncestors(): Sequence<Mutable> =
            sequence {
                yieldAll(getSequenceOfAncestorsWithoutIsAParents())
                for (isAParent in isAParents ?: setOf()) {
                    yield(isAParent)
                    yieldAll(isAParent.getSequenceOfAncestorsWithoutIsAParents())
                }
            }

        private fun getSequenceOfAncestorsWithoutIsAParents(): Sequence<Mutable> =
            sequence {
                for (parent in parents) {
                    yield(parent)
                    yieldAll(parent.getSequenceOfAncestorsWithoutIsAParents())
                }
            }

        override fun getSequenceOfDescendants(parameter: ChildrenParameter): Sequence<Mutable> =
            sequence {
                for (child in getSequenceOfChildren(parameter)) {
                    yield(child)
                    yieldAll(child.getSequenceOfDescendants(parameter))
                }
            }

        fun getSequenceOfLeafs(parameter: ChildrenParameter): Sequence<Mutable> =
            sequence {
                var yieldSelf = true
                for (child in getSequenceOfChildren(parameter)) {
                    yieldSelf = false
                    yieldAll(child.getSequenceOfLeafs(parameter))
                }
                if (yieldSelf) {
                    yield(this@Mutable)
                }
            }

        override fun getSequenceOfTwins(): Sequence<Mutable> = getSequenceOfTwins(false)

        fun getSequenceOfTwins(withSelf: Boolean): Sequence<Mutable> =
            sequence {
                if (withSelf) {
                    yield(this@Mutable)
                }
                var twin = this@Mutable.twin
                while (twin != null && twin != this@Mutable) {
                    yield(twin)
                    twin = twin.twin
                }
            }

        override fun toString(): String = "Tag:$friendlyName"
    }

    companion object {
        fun getSequenceOfAllParents(tag: Tag): Sequence<Tag> =
            CommonUtil.prependNullable(tag.isAParents?.asSequence(), tag.parents.asSequence())
    }

    class ChildrenParameter(
        val computed: Boolean,
        val defined: Boolean,
        val inherited: Boolean,
    ) {
        companion object {
            val all = ChildrenParameter(computed = true, defined = true, inherited = true)
            val defined = ChildrenParameter(computed = false, defined = true, inherited = false)
            val notComputed = ChildrenParameter(computed = false, defined = true, inherited = true)
        }
    }

    class Parameter private constructor(
        val canRename: Boolean,
        val implyDescendants: Boolean,
        val isExclusive: Boolean,
        val isPlaceholder: Boolean,
        val isRoot: Boolean,
        val priority: Int,
    ) {
        fun copy(
            canRename: Boolean? = null,
            implyDescendants: Boolean? = null,
            isExclusive: Boolean? = null,
            isPlaceholder: Boolean? = null,
            isRoot: Boolean? = null,
            priority: Int? = null,
        ): Parameter =
            cache(
                Parameter(
                    canRename = canRename ?: this.canRename,
                    implyDescendants = implyDescendants ?: this.implyDescendants,
                    isExclusive = isExclusive ?: this.isExclusive,
                    isPlaceholder = isPlaceholder ?: this.isPlaceholder,
                    isRoot = isRoot ?: this.isRoot,
                    priority = priority ?: this.priority,
                )
            )

        fun copy(latest: TagVersion1?): Parameter =
            if (latest == null) this else copy(
                canRename = latest.canRename,
                implyDescendants = latest.implyDescendants,
                isExclusive = latest.exclusive,
                isPlaceholder = latest.placeholder,
                priority = latest.priority,
            )

        fun merge(parameter: Parameter): Parameter =
            cache(
                Parameter(
                    canRename = parameter.canRename && this.canRename,
                    implyDescendants = parameter.implyDescendants || this.implyDescendants,
                    isExclusive = parameter.isExclusive || this.isExclusive,
                    isPlaceholder = parameter.isPlaceholder && this.isPlaceholder,
                    isRoot = parameter.isRoot || this.isRoot,
                    priority = parameter.priority.coerceAtLeast(this.priority),
                )
            )

        companion object {
            private val cache = mutableMapOf<Parameter, Parameter>()
            private fun cache(parameter: Parameter) = if (parameter.priority in 0..1) cache.computeIfAbsent(parameter) { it } else parameter
            val standard = cache(Parameter(canRename = true, implyDescendants = false, isExclusive = false, isPlaceholder = false, isRoot = false, priority = 0))
            val cannotRename = standard.copy(canRename = false)
            val placeholder = standard.copy(isPlaceholder = true)
            val root = standard.copy(isRoot = true)
            val system0 = cannotRename.copy(isRoot = true)
            private val system1 = system0.copy(priority = 1)
            val system = SystemTags.Impl(
                directory = system0,
                emptyContent = system0,
                emptyName = system0,
                emptyParentPath = system0,
                file = system0,
                input = system0,
                lostAndFound = system1,
                prune = system0,
                task = system0,
                taskDone = cannotRename,
                unknown = system1,
            )
        }
    }
}