package com.franzmandl.fileadmin.filter

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

class LockedItems(
    private val lock: ReentrantReadWriteLock,
    private val items: Iterable<Item>,
    val estimatedSize: Int,
) {
    fun findLocked(predicate: (Item) -> Boolean): Item? = lock.read { items.find(predicate) }

    fun forEachLocked(action: (Item) -> Unit) = lock.read { items.forEach(action) }
}