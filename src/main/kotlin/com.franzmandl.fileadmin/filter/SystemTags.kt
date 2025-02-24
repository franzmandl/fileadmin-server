package com.franzmandl.fileadmin.filter

interface SystemTags<T> {
    val directory: T
    val emptyContent: T
    val emptyName: T
    val emptyParentPath: T
    val file: T
    val input: T
    val lostAndFound: T
    val prune: T
    val task: T
    val taskDone: T
    val unknown: T

    class Impl<T>(
        override val directory: T,
        override val emptyContent: T,
        override val emptyName: T,
        override val emptyParentPath: T,
        override val file: T,
        override val input: T,
        override val lostAndFound: T,
        override val prune: T,
        override val task: T,
        override val taskDone: T,
        override val unknown: T,
    ) : SystemTags<T>

    companion object {
        fun <A, R> map(a: SystemTags<A>, transform: (A) -> R): SystemTags<R> =
            Impl(
                directory = transform(a.directory),
                emptyContent = transform(a.emptyContent),
                emptyName = transform(a.emptyName),
                emptyParentPath = transform(a.emptyParentPath),
                file = transform(a.file),
                input = transform(a.input),
                lostAndFound = transform(a.lostAndFound),
                prune = transform(a.prune),
                task = transform(a.task),
                taskDone = transform(a.taskDone),
                unknown = transform(a.unknown),
            )

        fun <A, B, R> map(a: SystemTags<A>, b: SystemTags<B>, transform: (A, B) -> R): SystemTags<R> =
            Impl(
                directory = transform(a.directory, b.directory),
                emptyContent = transform(a.emptyContent, b.emptyContent),
                emptyName = transform(a.emptyName, b.emptyName),
                emptyParentPath = transform(a.emptyParentPath, b.emptyParentPath),
                file = transform(a.file, b.file),
                input = transform(a.input, b.input),
                lostAndFound = transform(a.lostAndFound, b.lostAndFound),
                prune = transform(a.prune, b.prune),
                task = transform(a.task, b.task),
                taskDone = transform(a.taskDone, b.taskDone),
                unknown = transform(a.unknown, b.unknown),
            )
    }
}