package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.SafePath

sealed interface Tag {
    val name: String
    val parameter: Parameter
    val friendlyName: String
    val children: Set<Tag>
    val parents: Set<Tag>
    val isRoot: Boolean
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
        override val parameter: Parameter,
    ) : Tag {
        private var twin: Mutable? = null
        override val children = mutableSetOf<Mutable>()
        override val parents = mutableSetOf<Mutable>()
        override var isRoot: Boolean = false
        override val comparableName = FilterFileSystem.toComparableName(name)
        override val configFiles = mutableMapOf<SafePath, Inode>()
        override val sortableName = FilterFileSystem.toSortableName(name)

        override val friendlyName: String
            get() = FilterFileSystem.priorityPrefixString.repeat(parameter.priority) + name + (twin?.let { FilterFileSystem.aliasDeliminator + it.name } ?: "")

        fun addConfigFile(inode: Inode): Mutable {
            configFiles[inode.path] = inode
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

    class Parameter(val canRename: Boolean, val priority: Int) {
        companion object {
            val default = Parameter(true, 0)
            val system0 = Parameter(false, 0)
            val system1 = Parameter(false, 1)
        }
    }
}