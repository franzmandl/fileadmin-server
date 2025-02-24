package com.franzmandl.fileadmin.filter

@JvmInline
value class LockedItemsList(private val list: List<LockedItems>) {
    fun find(predicate: (Item) -> Boolean): Item? {
        for (lockedItems in list) {
            val item = lockedItems.findLocked(predicate)
            if (item != null) {
                return item
            }
        }
        return null
    }

    fun forEach(action: (Item) -> Unit) {
        for (lockedItems in list) {
            lockedItems.forEachLocked(action)
        }
    }

    fun toList(): List<Item> = toMutableList()

    fun toMutableList(): MutableList<Item> {
        val items = ArrayList<Item>(list.fold(0) { acc, it -> acc + it.estimatedSize })
        for (lockedItems in list) {
            lockedItems.forEachLocked(items::add)
        }
        return items
    }
}