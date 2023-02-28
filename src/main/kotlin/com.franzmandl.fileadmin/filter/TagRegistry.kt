package com.franzmandl.fileadmin.filter

class TagRegistry(
    tagDirectoryName: String,
    tagFileName: String,
    tagInputName: String,
    tagLostAndFoundName: String,
    tagUnknownName: String,
) {
    private val mutableTags = mutableMapOf<String, Tag.Mutable>()
    val tags: Map<String, Tag> = mutableTags
    val tagDirectory = getOrCreateSystemTag(tagDirectoryName, Tag.Parameter.system0)
    val tagFile = getOrCreateSystemTag(tagFileName, Tag.Parameter.system0)
    val tagInput = getOrCreateSystemTag(tagInputName, Tag.Parameter.system0)
    val tagLostAndFound = getOrCreateSystemTag(tagLostAndFoundName, Tag.Parameter.system1)
    val tagUnknown = getOrCreateSystemTag(tagUnknownName, Tag.Parameter.system1)

    fun getTag(name: String): Tag.Mutable? = mutableTags[name]

    private fun createTag(name: String, parameter: Tag.Parameter): Tag.Mutable =
        Tag.Mutable(name, parameter).also { mutableTags[name] = it }

    private fun getOrCreateSystemTag(name: String, parameter: Tag.Parameter): Tag.Mutable =
        getOrCreateTag(name, parameter) { it.isRoot = true }

    fun getOrCreateTag(name: String, parameter: Tag.Parameter, init: ((Tag.Mutable) -> Unit)? = null): Tag.Mutable =
        getTag(name) ?: createTag(name, parameter).also {
            if (init != null) {
                init(it)
            }
        }

    fun clearIf(condition: Boolean) {
        if(condition) {
            clear()
        }
    }

    private fun clear() {
        for (tag in tagUnknown.children) {
            mutableTags.remove(tag.name)
        }
        tagUnknown.children.clear()
    }
}