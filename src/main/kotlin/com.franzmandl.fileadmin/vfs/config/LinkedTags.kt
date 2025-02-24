package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.config.TagVersion1
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.vfs.SafePath

class LinkedTags(
    private val errorHandler: ErrorHandler,
    private val explicitParentsList: List<List<Tag.Mutable>>,
    private val isATagsList: List<List<Tag.Mutable>>,
    private val overrideParentsList: List<List<Tag.Mutable>>?,
) {
    fun createTagSymbolResolver(implicitParents: List<Tag.Mutable>, name: String): TagSymbolResolver =
        TagSymbolResolver(
            explicitParentsList = explicitParentsList, implicitParents = implicitParents,
            isATagsList = isATagsList, name = name, overrideParentsList = overrideParentsList
        )

    fun link(tags: List<Tag.Mutable>, configPath: SafePath, latest: TagVersion1, implicitParents: List<Tag.Mutable>) {
        val spread = latest.spread == true
        for (tag in tags) {
            linkIsAList(configPath, tag, isATagsList)
            linkParentsList(configPath, tag, explicitParentsList, spread)
            linkParentsList(configPath, tag, overrideParentsList ?: listOf(implicitParents), spread)
        }
    }

    private fun linkIsAList(configPath: SafePath, tag: Tag.Mutable, isATagsList: List<List<Tag.Mutable>>) {
        for (isATags in isATagsList) {
            for (isATag in isATags) {
                LinkTagHelper.linkIsAParent(configPath, tag, isATag, errorHandler)
            }
        }
    }

    private fun linkParentsList(configPath: SafePath, tag: Tag.Mutable, parentsList: List<List<Tag.Mutable>>, spread: Boolean) {
        for (parents in parentsList) {
            for (parent in parents) {
                if (spread) {
                    parent.addChildrenOfTag(tag)
                }
                LinkTagHelper.linkParent(configPath, tag, parent, false, errorHandler)
            }
        }
    }
}