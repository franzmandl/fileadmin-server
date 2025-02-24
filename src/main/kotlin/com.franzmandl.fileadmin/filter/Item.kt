package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.vfs.Inode1
import java.time.LocalDate

class Item(
    val inode: Inode1<*>,
    val input: Input,
    val automaticTags: ItemTags,
    val contentConsecutiveTags: List<List<Tag>>,
    val contentTags: ItemTags,
    val hasContent: Boolean,
    val nameConsecutiveTags: List<List<Tag>>,
    val nameTags: ItemTags,
    val parentPathConsecutiveTags: List<List<Tag>>,
    val parentPathTags: ItemTags,
    val parentPathStartIndex: Int,
    val time: LocalDate?,
) {
    val automaticConsecutiveTags = automaticTags.all.map { listOf(it) }
    val anyConsecutiveTags: List<List<Tag>> = CommonUtil.concatenate(contentConsecutiveTags, nameConsecutiveTags, automaticConsecutiveTags, parentPathConsecutiveTags)
    val allTags: ItemTags = ItemTags.Mutable().addAll(automaticTags).addAll(contentTags).addAll(nameTags).addAll(parentPathTags)

    override fun toString(): String = "Item:${inode.inode0.path} $allTags"

    companion object {
        fun create(
            registry: TagRegistry.Phase2,
            inode: Inode1<*>,
            inodeTag: Tag?,
            input: Input,
            parentPathStartIndex: Int,
            getContent: () -> CharSequence,
            errorHandler: ErrorHandler,
            time: LocalDate?,
        ): Item {
            val automaticTags = ItemTags.Mutable()
            inodeTag?.let { automaticTags.addTag(it) }
            input.automaticTags.forEach { automaticTags.addTag(it) }
            val nameTags = ItemTags.Mutable()
            val nameConsecutiveTags = if (input.scanNameForTags) {
                getSequenceOfConsecutiveTags(registry, nameTags, inode.inode0.path.name, 0, errorHandler).toList()
            } else listOf()
            val scanContentForTags = registry.systemTags.prune !in nameTags.all
            val contentTags = ItemTags.Mutable()
            val contentConsecutiveTags = if (scanContentForTags) {
                getSequenceOfConsecutiveTags(registry, contentTags, FilterFileSystem.replaceUrlsWithFragment(getContent()), 0, errorHandler).toList()
            } else listOf()
            val parentPathTags = ItemTags.Mutable()
            val parentPathConsecutiveTags = if (input.scanParentPathForTags) {
                getSequenceOfConsecutiveTags(registry, parentPathTags, inode.inode0.path.parent?.absoluteString ?: "", parentPathStartIndex, errorHandler).toList()
            } else listOf()
            if (input.lostAndFound != null && contentTags.all.isEmpty() && nameTags.all.isEmpty() && parentPathTags.all.isEmpty()) {
                automaticTags.addTag(input.lostAndFound)
            }
            if (scanContentForTags && contentTags.all.isEmpty()) {
                contentTags.addTag(registry.systemTags.emptyContent)
            }
            if (nameTags.all.isEmpty() || registry.pruneSystemTags.containsAll(nameTags.all)) {
                nameTags.addTag(registry.systemTags.emptyName)
            }
            if (parentPathTags.all.isEmpty()) {
                parentPathTags.addTag(registry.systemTags.emptyParentPath)
            }
            if (inode.inode0.isDirectory) {
                automaticTags.addTag(registry.systemTags.directory)
            }
            if (inode.inode0.isFile) {
                automaticTags.addTag(registry.systemTags.file)
            }
            return Item(
                inode,
                input,
                automaticTags,
                contentConsecutiveTags,
                contentTags,
                scanContentForTags,
                nameConsecutiveTags,
                nameTags,
                parentPathConsecutiveTags,
                parentPathTags,
                parentPathStartIndex,
                time,
            )
        }

        private fun getSequenceOfConsecutiveTags(
            registry: TagRegistry,
            tags: ItemTags.Mutable,
            value: String,
            startIndex: Int,
            errorHandler: ErrorHandler
        ): Sequence<List<Tag.Mutable>> =
            FilterFileSystem.getSequenceOfConsecutiveTagNames(value, startIndex).map { stringRanges ->
                stringRanges.map { stringRange ->
                    val tag = registry.getOrCreateTag(stringRange.value, Tag.Parameter.placeholder, errorHandler) {
                        it.addParent(registry.systemTags.unknown, false)
                        it.parameter = Tag.Parameter.standard
                    }
                    tags.addTag(tag)
                    tag
                }
            }
    }
}