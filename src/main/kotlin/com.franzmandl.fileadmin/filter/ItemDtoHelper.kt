package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.dto.ItemDto
import com.franzmandl.fileadmin.dto.ItemResultDto
import com.franzmandl.fileadmin.filter.rating.TagFilterRelatives
import com.franzmandl.fileadmin.vfs.SafePath

object ItemDtoHelper {
    fun createItemDto(outputPath: SafePath, item: Item?, filterResult: FilterResult?, overrideTime: String?) =
        ItemDto(
            outputPath = outputPath,
            result = filterResult?.let { getItemResultDto(it, item) },
            tags = item?.allTags?.all?.mapTo(mutableSetOf()) { it.name },
            time = overrideTime ?: item?.time?.toString(),
        )

    private fun getItemResultDto(result: FilterResult, item: Item?): ItemResultDto =
        ItemResultDto(
            highlightTags = result.highlightTagNames,
            priority = item?.let { rateItem(it, result) },
        )

    private fun rateItem(item: Item, result: FilterResult): Int {
        var maximumRating = 0
        for (reason in TagFilter.Reason.entries) {
            val consecutiveFilters = result.consecutiveFilters[reason]
            for (consecutiveTags in reason.getConsecutiveTags(item)) {
                for (consecutiveFilter in consecutiveFilters) {
                    maximumRating =
                        rateConsecutiveTags(consecutiveFilter, consecutiveTags).coerceAtLeast(maximumRating)
                }
            }
        }
        return maximumRating
    }

    private fun rateConsecutiveTags(consecutiveFilter: List<TagFilterRelatives>, consecutiveTags: List<Tag>): Int {
        var rating = 0
        for (filterTag in consecutiveFilter) {
            for (itemTag in consecutiveTags) {
                rating += when (itemTag) {
                    in filterTag.category0Tags -> 20
                    in filterTag.category1Tags -> 10
                    in filterTag.category2Tags -> 8
                    in filterTag.category3Tags -> 6
                    else -> 0
                }
            }
        }
        return rating
    }
}