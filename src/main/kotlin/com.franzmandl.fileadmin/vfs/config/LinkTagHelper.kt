package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.vfs.SafePath

object LinkTagHelper {
    fun linkTwins(iterator: Iterator<Tag.Mutable>) {
        val tag = if (iterator.hasNext()) iterator.next() else return
        while (iterator.hasNext()) {
            linkTwin(tag, iterator.next())
        }
    }

    fun linkTwin(tag: Tag.Mutable, twin: Tag.Mutable) {
        if (tag != twin && !twin.getSequenceOfTwins().any { it == tag }) {
            twin.setTwin(tag)
        }
    }

    fun linkParents(configPath: SafePath, tags: Iterable<Tag.Mutable>, parents: Iterable<Tag.Mutable>, isComputed: Boolean, errorHandler: ErrorHandler) {
        for (tag in tags) {
            for (parent in parents) {
                linkParent(configPath, tag, parent, isComputed, errorHandler)
            }
        }
    }

    fun linkParent(configPath: SafePath, tag: Tag.Mutable, parent: Tag.Mutable, isComputed: Boolean, errorHandler: ErrorHandler): Tag.Mutable {
        if (tag != parent && !tag.getSequenceOfDescendants(Tag.ChildrenParameter.all).any { it == parent }) {
            tag.addParent(parent, isComputed)
        } else {
            errorHandler.onError("${configPath.absoluteString}: Parent loop detected between ${tag.friendlyName} and ${parent.friendlyName}.")
        }
        return tag
    }

    fun linkIsAParent(configPath: SafePath, tag: Tag.Mutable, parent: Tag.Mutable, errorHandler: ErrorHandler): Tag.Mutable {
        if (tag != parent && !tag.getSequenceOfDescendants(Tag.ChildrenParameter.all).any { it == parent }) {
            tag.addIsAParent(parent)
        } else {
            errorHandler.onError("${configPath.absoluteString}: Is-a loop detected between ${tag.friendlyName} and ${parent.friendlyName}.")
        }
        return tag
    }
}