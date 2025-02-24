package com.franzmandl.fileadmin.common

import java.util.*

object RegexUtil {
    fun <T> getSequenceOfGroupedConsecutiveMatchResults(matchResults: Sequence<MatchResult>, transform: (MatchResult) -> T): Sequence<List<T>> {
        val iterator = matchResults.iterator()
        if (!iterator.hasNext()) {
            return sequenceOf()
        }
        var previous = iterator.next()
        if (!iterator.hasNext()) {
            return sequenceOf(listOf(transform(previous)))
        }
        var builder = LinkedList<T>()
        builder += transform(previous)
        return sequence {
            while (iterator.hasNext()) {
                val current = iterator.next()
                if (previous.range.last != current.range.first - 1) {
                    yield(optimizeList(builder))
                    builder = LinkedList()
                }
                builder += transform(current)
                previous = current
            }
            yield(optimizeList(builder))
        }
    }

    private fun <T> optimizeList(list: List<T>): List<T> =
        when (list.size) {
            0 -> emptyList()
            1 -> listOf(list[0])
            else -> list
        }
}