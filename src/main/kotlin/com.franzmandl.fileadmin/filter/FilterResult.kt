package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.filter.rating.FilterRatingUtil

class FilterResult(
    val canRename: Boolean,
    val childSet: FilterChildSet,
    val context: FilterCtx,
    val filters: List<Filter>,
    val filterStrings: List<String>,
) {
    val consecutiveFilters by lazy {
        FilterRatingUtil.getConsecutiveFilters(filters)
    }

    val highlightTagNames by lazy {
        val highlightTags = mutableSetOf<Tag>()
        for (filter in CommonUtil.getReversedSequenceOf(filters)) {
            if (filter is TagFilter && filter.tag !in highlightTags) {
                highlightTags += filter.tag.getSequenceOfAncestors()
                highlightTags += filter.tag.getSequenceOfDescendants(Tag.ChildrenParameter.all)
                highlightTags += filter.tag.getSequenceOfTwins()
                highlightTags += filter.tag
            }
        }
        highlightTags.map { it.name }
    }
}