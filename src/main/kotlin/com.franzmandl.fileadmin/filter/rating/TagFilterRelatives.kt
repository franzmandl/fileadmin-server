package com.franzmandl.fileadmin.filter.rating

import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.filter.TagFilter

class TagFilterRelatives(filter: TagFilter) {
    val category0Tags = mutableSetOf<Tag>()
    val category1Tags = mutableSetOf<Tag>()
    val category2Tags = mutableSetOf<Tag>()
    val category3Tags = mutableSetOf<Tag>()

    init {
        when (filter.relationship) {
            TagFilter.Relationship.Any -> {
                category3Tags += filter.tag.getSequenceOfAncestors()
                category3Tags += filter.tag.getSequenceOfDescendants(Tag.ChildrenParameter.all)
                category2Tags += filter.tag.getSequenceOfTwins()
                category1Tags += filter.tag
            }

            TagFilter.Relationship.Ancestor -> category2Tags += filter.tag.getSequenceOfAncestors()
            TagFilter.Relationship.Descendant -> category2Tags += filter.tag.getSequenceOfDescendants(Tag.ChildrenParameter.all)
            TagFilter.Relationship.Self -> category0Tags += filter.tag
            TagFilter.Relationship.Twin -> category2Tags += filter.tag.getSequenceOfTwins()
        }
    }
}