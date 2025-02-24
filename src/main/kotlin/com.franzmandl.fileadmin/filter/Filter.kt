package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.resource.RequestCtx
import java.time.LocalDate

sealed interface Filter {
    fun isKept(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item, errorHandler: ErrorHandler): Boolean
}

class ElseFilter(
    val tag: Tag,
) : Filter {
    private val descendants: Set<Tag> = tag.getSequenceOfDescendants(Tag.ChildrenParameter.all).toSet()

    override fun isKept(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item, errorHandler: ErrorHandler): Boolean {
        if (tag in item.allTags.tags || tag in item.allTags.twins) {
            return true
        }
        for (descendant in descendants) {
            if (descendant in item.allTags.all) {
                return false
            }
        }
        return true
    }
}

class MaxFilter(
    private val maximum: Int
) : Filter {
    private var count = 0

    override fun isKept(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item, errorHandler: ErrorHandler): Boolean =
        count++ < maximum
}

class NotFilter(
    val filter: Filter
) : Filter {
    override fun isKept(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item, errorHandler: ErrorHandler): Boolean =
        !filter.isKept(requestCtx, filterCtx, item, errorHandler)
}

class TagFilter(
    val tag: Tag,
    val reason: Reason,
    val relationship: Relationship,
) : Filter {
    override fun isKept(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item, errorHandler: ErrorHandler): Boolean =
        tag in relationship.getSet(reason.getTags(item))

    enum class Reason {
        Any,
        Automatic,
        Content,
        Name,
        ParentPath,
        ;

        fun getConsecutiveTags(item: Item): List<List<Tag>> = when (this) {
            Any -> item.anyConsecutiveTags
            Automatic -> item.automaticConsecutiveTags
            Content -> item.contentConsecutiveTags
            Name -> item.nameConsecutiveTags
            ParentPath -> item.parentPathConsecutiveTags
        }

        fun getTags(item: Item): ItemTags = when (this) {
            Any -> item.allTags
            Automatic -> item.automaticTags
            Content -> item.contentTags
            Name -> item.nameTags
            ParentPath -> item.parentPathTags
        }
    }

    enum class Relationship {
        Any,
        Ancestor,
        Descendant,
        Self,
        Twin,
        ;

        fun getSet(tags: ItemTags): Set<Tag> = when (this) {
            Any -> tags.all
            Ancestor -> tags.ancestors
            Descendant -> tags.descendants
            Self -> tags.tags
            Twin -> tags.twins
        }
    }
}

class TextFilter(
    val reason: Reason,
    val regex: Regex,
) : Filter {
    override fun isKept(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item, errorHandler: ErrorHandler): Boolean {
        return when (reason) {
            Reason.Content -> containsMatchInContent(requestCtx, item, errorHandler)
            Reason.ContentOrName -> containsMatchInName(item) || containsMatchInContent(requestCtx, item, errorHandler)
            Reason.MimeType -> regex.containsMatchIn(item.inode.inode0.mimeType)
            Reason.Name -> containsMatchInName(item)
            Reason.Path -> regex.containsMatchIn(item.inode.inode0.path.absoluteString)
        }
    }

    private fun containsMatchInContent(requestCtx: RequestCtx, item: Item, errorHandler: ErrorHandler) =
        item.hasContent && regex.containsMatchIn(item.input.getItemContent(requestCtx, item.inode, CommonUtil::noop, errorHandler))

    private fun containsMatchInName(item: Item) =
        regex.containsMatchIn(item.inode.inode0.path.name)

    enum class Reason {
        Content,
        ContentOrName,
        MimeType,
        Name,
        Path,
        ;
    }
}

class TimeFilter(
    val operator: CompareOperator,
    val time: LocalDate,
) : Filter {
    override fun isKept(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item, errorHandler: ErrorHandler): Boolean {
        return operator.apply(item.time ?: return false, time)
    }
}