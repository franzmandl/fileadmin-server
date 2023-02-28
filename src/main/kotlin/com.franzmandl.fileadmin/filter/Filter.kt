package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.vfs.Inode
import java.time.LocalDate

sealed interface Filter {
    fun isKept(ctx: FilterCtx, item: Item, onError: (String) -> Unit): Boolean
}

class ContentFilter(
    val operator: StringOperator,
    val value: String,
) : Filter {
    override fun isKept(ctx: FilterCtx, item: Item, onError: (String) -> Unit): Boolean =
        operator.apply(getContent(item.inode, onError), value)

    private fun getContent(inode: Inode, onError: (String) -> Unit): String =
        when {
            inode.contentPermission.canFileGet -> inode.text
            inode.contentPermission.canDirectoryGet -> Inode.getChildrenAsText(inode)
            else -> {
                onError("${inode.path} is neither a directory nor a file or insufficient permission.")
                ""
            }
        }
}

class ElseFilter(
    val tag: Tag,
) : Filter {
    private val descendants: Set<Tag> = tag.addDescendantsTo(mutableSetOf())

    override fun isKept(ctx: FilterCtx, item: Item, onError: (String) -> Unit): Boolean {
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

class NotFilter(
    val filter: Filter
) : Filter {
    override fun isKept(ctx: FilterCtx, item: Item, onError: (String) -> Unit): Boolean =
        !filter.isKept(ctx, item, onError)
}

class PathFilter(
    val operator: StringOperator,
    val value: String,
) : Filter {
    override fun isKept(ctx: FilterCtx, item: Item, onError: (String) -> Unit): Boolean =
        operator.apply(item.inode.path.absoluteString, value)
}

class TagFilter(
    val tag: Tag,
    val reason: Reason,
    val relationship: Relationship,
) : Filter {
    enum class Reason {
        Any,
        Automatic,
        Content,
        Name,
        ParentPath,
        ;

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

    override fun isKept(ctx: FilterCtx, item: Item, onError: (String) -> Unit): Boolean =
        tag in relationship.getSet(reason.getTags(item))
}

class TimeFilter(
    val time: LocalDate,
    val operator: CompareOperator,
) : Filter {
    override fun isKept(ctx: FilterCtx, item: Item, onError: (String) -> Unit): Boolean {
        return operator.apply(item.time ?: return false, time)
    }
}