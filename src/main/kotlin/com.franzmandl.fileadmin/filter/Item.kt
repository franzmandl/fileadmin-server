package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.vfs.Inode
import java.util.*

class Item(
    val inode: Inode,
    val automaticTags: ItemTags,
    val contentTags: ItemTags,
    val nameTags: ItemTags,
    val parentPathTags: ItemTags,
    val parentPathStartIndex: Int,
) {
    val allTags: ItemTags = ItemTags.Mutable().addAll(automaticTags).addAll(contentTags).addAll(nameTags).addAll(parentPathTags)
    val time = CommonUtil.parseDate(inode.path.name)

    override fun toString(): String = "Item:${inode.path} $allTags"

    companion object {
        fun create(
            registry: TagRegistry,
            inode: Inode,
            automaticTags: ItemTags.Mutable,
            parentPathStartIndex: Int,
            content: String?,
        ): Item {
            val contentTags = createTags(registry, if (content != null) createContentStringRangeMap(content) else mapOf())
            val nameTags = createTags(registry, createStringRangeMap(inode.path.name, 0))
            val parentPathTags = createTags(registry, createStringRangeMap(inode.path.parent?.absoluteString ?: "", parentPathStartIndex))
            if (contentTags.all.isEmpty() && nameTags.all.isEmpty() && parentPathTags.all.isEmpty()) {
                automaticTags.addTag(registry.tagLostAndFound, false)
            }
            if (inode.isDirectory) {
                automaticTags.addTag(registry.tagDirectory, false)
            }
            if (inode.isFile) {
                automaticTags.addTag(registry.tagFile, false)
            }
            return Item(
                inode,
                automaticTags,
                contentTags,
                nameTags,
                parentPathTags,
                parentPathStartIndex,
            )
        }

        private fun createContentStringRangeMap(content: String): Map<String, List<StringRange<Boolean>>> =
            createStringRangeMap(content.replace(FilterFileSystem.urlWithFragmentRegex) { FilterFileSystem.urlWithFragmentReplacementString.repeat(it.value.length) }, 0)

        private fun createStringRangeMap(value: String, startIndex: Int): Map<String, List<StringRange<Boolean>>> {
            val stringRangeMap = mutableMapOf<String, MutableList<StringRange<Boolean>>>()
            TagVisitor.visit(value, startIndex) { stringRange ->
                stringRangeMap.computeIfAbsent(stringRange.value) { LinkedList() }.add(stringRange)
            }
            return stringRangeMap
        }

        private fun createTags(registry: TagRegistry, names: Map<String, List<StringRange<Boolean>>>): ItemTags.Mutable {
            val tags = ItemTags.Mutable()
            for (name in names) {
                val tag = registry.getOrCreateTag(name.value.firstOrNull()?.value ?: name.key, Tag.Parameter.default) { it.addParent(registry.tagUnknown) }
                tags.addTag(tag, name.value.any { it.payload })
            }
            return tags
        }
    }
}