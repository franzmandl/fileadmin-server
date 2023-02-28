package com.franzmandl.fileadmin.filter

interface ItemTags {
    val all: Set<Tag>
    val ancestors: Set<Tag>
    val descendants: Set<Tag>
    val tags: Set<Tag>
    val twins: Set<Tag>

    class Mutable : ItemTags {
        private val mutableAll = mutableSetOf<Tag>()
        private val mutableAncestors = mutableSetOf<Tag>()
        private val mutableDescendants = mutableSetOf<Tag>()
        private val mutableTags = mutableSetOf<Tag>()
        private val mutableTwins = mutableSetOf<Tag>()
        override val all: Set<Tag> = mutableAll
        override val ancestors: Set<Tag> = mutableAncestors
        override val descendants: Set<Tag> = mutableDescendants
        override val tags: Set<Tag> = mutableTags
        override val twins: Set<Tag> = mutableTwins

        fun addTag(tag: Tag, addDescendants: Boolean): Mutable =
            add(tag.addAncestorsTo(mutableSetOf()), if (addDescendants) tag.addDescendantsTo(mutableSetOf()) else setOf(), tag, tag.addTwinsTo(mutableSetOf()))

        fun addAll(other: ItemTags): Mutable {
            mutableAll.addAll(other.all)
            mutableAncestors.addAll(other.ancestors)
            mutableDescendants.addAll(other.descendants)
            mutableTags.addAll(other.tags)
            mutableTwins.addAll(other.twins)
            return this
        }

        private fun add(ancestors: Set<Tag>, descendants: Set<Tag>, tag: Tag, twins: Set<Tag>): Mutable {
            mutableAll.addAll(ancestors)
            mutableAll.addAll(descendants)
            mutableAll.add(tag)
            mutableAll.addAll(twins)
            mutableAncestors.addAll(ancestors)
            mutableDescendants.addAll(descendants)
            mutableTags.add(tag)
            mutableTwins.addAll(twins)
            return this
        }

        override fun toString(): String = "$all"
    }
}