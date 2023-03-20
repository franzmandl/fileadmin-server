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
    val tagDirectory = getOrCreateTag(tagDirectoryName).apply { parameter.initSystem(0) }
    val tagFile = getOrCreateTag(tagFileName).apply { parameter.initSystem(0) }
    val tagInput = getOrCreateTag(tagInputName).apply { parameter.initSystem(0) }
    val tagLostAndFound = getOrCreateTag(tagLostAndFoundName).apply { parameter.initSystem(1) }
    val tagUnknown = getOrCreateTag(tagUnknownName).apply { parameter.initSystem(1) }

    fun getTag(name: String): Tag.Mutable? = mutableTags[name]

    private fun createTag(name: String): Tag.Mutable =
        Tag.Mutable(name).also { mutableTags[name] = it }

    fun getOrCreateTag(name: String, init: ((Tag.Mutable) -> Unit)? = null): Tag.Mutable =
        getTag(name) ?: createTag(name).also {
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