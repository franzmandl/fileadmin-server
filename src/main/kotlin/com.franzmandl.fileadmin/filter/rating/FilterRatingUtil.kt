package com.franzmandl.fileadmin.filter.rating

import com.franzmandl.fileadmin.common.EnumCollection
import com.franzmandl.fileadmin.filter.Filter
import com.franzmandl.fileadmin.filter.TagFilter
import java.util.*

object FilterRatingUtil {
    fun getConsecutiveFilters(filters: List<Filter>): ConsecutiveFilters {
        var mainReason = TagFilter.Reason.Any
        var mainBuilder = LinkedList<TagFilterRelatives>()
        var subBuilder = LinkedList<TagFilterRelatives>()
        val consecutiveFilters = EnumCollection(TagFilter.Reason::class) { LinkedList<List<TagFilterRelatives>>() }
        fun addMainBuilder() {
            if (mainBuilder.isNotEmpty()) {
                consecutiveFilters[mainReason].add(mainBuilder)
            }
        }
        for (filter in filters) {
            if (filter is TagFilter) {
                val relatives = TagFilterRelatives(filter)
                when (filter.reason) {
                    TagFilter.Reason.Any -> {
                        mainBuilder.add(relatives)
                        subBuilder.add(relatives)
                    }

                    TagFilter.Reason.Automatic -> {
                        // Acts as a terminal: resets values to initial values.
                        addMainBuilder()
                        consecutiveFilters[TagFilter.Reason.Automatic].add(listOf(relatives))
                        mainReason = TagFilter.Reason.Any
                        mainBuilder = LinkedList()
                        subBuilder = LinkedList()
                    }

                    TagFilter.Reason.Content, TagFilter.Reason.Name, TagFilter.Reason.ParentPath -> {
                        when (mainReason) {
                            TagFilter.Reason.Any -> {
                                mainReason = filter.reason
                                mainBuilder.add(relatives)
                                subBuilder.add(relatives)
                            }
                            filter.reason -> {
                                mainBuilder.add(relatives)
                                subBuilder = LinkedList()
                            }
                            else -> {
                                addMainBuilder()
                                mainReason = filter.reason
                                mainBuilder = subBuilder
                                mainBuilder.add(relatives)
                                subBuilder = LinkedList()
                            }
                        }
                    }
                }
            }
        }
        addMainBuilder()
        return consecutiveFilters
    }
}