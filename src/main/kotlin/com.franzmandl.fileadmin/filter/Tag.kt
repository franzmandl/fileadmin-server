package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.SafePath

sealed interface Tag {
    val name: String
    val parameter: Parameter
    val friendlyName: String
    val children: Set<Tag>
    val childrenOf: Set<Tag>
    val parents: Set<Tag>
    val comparableName: String
    val configFiles: Map<SafePath, Inode>
    val sortableName: String
    fun <T : MutableCollection<Tag>> addAncestorsTo(tags: T): T
    fun <T : MutableCollection<Tag>> addDescendantsTo(tags: T): T
    fun <T : MutableCollection<Tag>> addTwinsTo(tags: T): T
    fun hasAnyDescendant(tags: Set<Tag>): Boolean
    fun hasDescendant(tag: Tag): Boolean
    fun hasTwin(tag: Tag): Boolean

    class Mutable(
        override val name: String,
    ) : Tag {
        private var twin: Mutable? = null
        override val children = mutableSetOf<Mutable>()
        override val childrenOf = mutableSetOf<Mutable>()
        override val parameter = Parameter.Mutable(canRename = true, isRoot = false, priority = 0)
        override val parents = mutableSetOf<Mutable>()
        override val comparableName = FilterFileSystem.toComparableName(name)
        override val configFiles = mutableMapOf<SafePath, Inode>()
        override val sortableName = FilterFileSystem.toSortableName(name)

        override val friendlyName: String
            get() = FilterFileSystem.priorityPrefixString.repeat(parameter.priority) + name + (twin?.let { FilterFileSystem.aliasDeliminator + it.name } ?: "")

        fun addConfigFile(inode: Inode): Mutable {
            configFiles[inode.path] = inode
            return this
        }

        fun addChildrenOf(tag: Mutable): Mutable {
            this.childrenOf.add(tag)
            return this
        }

        fun addParent(parent: Mutable): Mutable {
            this.parents.add(parent)
            parent.children.add(this)
            return this
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

        override fun <T : MutableCollection<Tag>> addAncestorsTo(tags: T): T {
            for (parent in parents) {
                tags.add(parent)
                parent.addAncestorsTo(tags)
            }
            return tags
        }

        override fun <T : MutableCollection<Tag>> addDescendantsTo(tags: T): T {
            for (child in children) {
                tags.add(child)
                child.addDescendantsTo(tags)
            }
            return tags
        }

        override fun <T : MutableCollection<Tag>> addTwinsTo(tags: T): T {
            var twin = this.twin
            while (twin != null && twin != this) {
                tags.add(twin)
                twin = twin.twin
            }
            return tags
        }

        override fun hasAnyDescendant(tags: Set<Tag>): Boolean {
            for (child in children) {
                if (child in tags || child.hasAnyDescendant(tags)) {
                    return true
                }
            }
            return false
        }

        override fun hasDescendant(tag: Tag): Boolean =
            hasAnyDescendant(setOf(tag))

        override fun hasTwin(tag: Tag): Boolean {
            var twin = this.twin
            while (twin != null && twin != this) {
                if (twin == tag) {
                    return true
                }
                twin = twin.twin
            }
            return false
        }

        override fun toString(): String = "Tag:$friendlyName"
    }

    sealed interface Parameter {
        val canRename: Boolean
        val isRoot: Boolean
        val priority: Int

        class Mutable(
            override var canRename: Boolean,
            override var isRoot: Boolean,
            override var priority: Int,
        ) : Parameter {
            fun initSystem(priority: Int) {
                canRename = false
                isRoot = true
                this.priority = priority
            }

            fun setCanRename(canRename: Boolean?) {
                if (canRename != null) {
                    this.canRename = canRename
                }
            }

            fun setPriority(priority: Int?) {
                if (priority != null) {
                    this.priority = priority
                }
            }
        }
    }
}