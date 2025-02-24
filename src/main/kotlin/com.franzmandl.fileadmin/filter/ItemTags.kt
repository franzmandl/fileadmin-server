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

        fun addTag(tag: Tag): Mutable =
            add(
                tag.getSequenceOfAncestors(),
                if (tag.parameter.implyDescendants) tag.getSequenceOfDescendants(Tag.ChildrenParameter.notComputed) else sequenceOf(),
                tag,
                tag.getSequenceOfTwins()
            )

        fun addAll(other: ItemTags): Mutable {
            mutableAll += other.all
            mutableAncestors += other.ancestors
            mutableDescendants += other.descendants
            mutableTags += other.tags
            mutableTwins += other.twins
            return this
        }

        private fun add(ancestors: Sequence<Tag>, descendants: Sequence<Tag>, tag: Tag, twins: Sequence<Tag>): Mutable {
            mutableAll += ancestors
            mutableAll += descendants
            mutableAll += tag
            mutableAll += twins
            mutableAncestors += ancestors
            mutableDescendants += descendants
            mutableTags += tag
            mutableTwins += twins
            return this
        }

        override fun toString(): String = "$all"
    }
}