package com.franzmandl.fileadmin.filter

object TagVisitor {
    fun visit(value: String, startIndex: Int, callback: (StringRange<Boolean>) -> Unit) {
        FilterFileSystem.tagRegex.findAll(value.substring(startIndex)).forEach {
            val addDescendants = it.groups[1] != null
            val nameGroup = it.groups[2]!!
            callback(StringRange(nameGroup.value, startIndex + nameGroup.range.first, startIndex + nameGroup.range.last, addDescendants))
        }
    }
}