package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.vfs.SafePath
import com.franzmandl.fileadmin.vfs.VirtualDirectory

interface FilterChildSet : VirtualDirectory.ChildSet {
    val items: List<Item>

    class Simple(
        override val children: Set<SafePath>,
        override val estimatedSize: Int?,
        override val size: Int,
    ) : FilterChildSet {
        override val items = listOf<Item>()

        companion object {
            val empty = createSameSize(setOf())

            fun createSameSize(children: Set<SafePath>): Simple =
                Simple(children, children.size, children.size)
        }
    }
}