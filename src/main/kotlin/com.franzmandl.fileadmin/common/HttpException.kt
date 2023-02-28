package com.franzmandl.fileadmin.common

import com.franzmandl.fileadmin.vfs.SafePath
import org.springframework.http.HttpStatus

class HttpException(
    val status: HttpStatus,
    override val message: String,
) : RuntimeException(message) {
    companion object {
        fun alreadyExists(): BuilderStart = Builder("Inode already exists.")
        fun badRequest(message: String) = HttpException(HttpStatus.BAD_REQUEST, message)
        fun directoryNotEmpty(): BuilderStart = Builder("Directory is not empty.")
        fun getMessage(e: Exception): String = if (e is HttpException) e.message else "See log file."
        fun notAllowed(): BuilderStart = Builder("Not allowed.")
        fun notAllowedDestination(): BuilderStart = Builder("Destination does not allow.")
        fun notExists(): BuilderStart = Builder("Inode does not exist.")
        fun notSupported(): BuilderStart = Builder("Not supported.")
        fun parentNotSupported(): BuilderStart = Builder("Parent does not support.")
        fun processError(message: String): BuilderStart = Builder("Process error: $message")
        fun processTimeout(): BuilderStart = Builder("Process timeout.")
    }

    interface BuilderStart {
        fun copy(value: SafePath): BuilderTo
        fun children(value: SafePath): BuilderEnd
        fun create(value: SafePath): BuilderEnd
        fun delete(value: SafePath): BuilderEnd
        fun getFile(value: SafePath): BuilderEnd
        fun lastModified(value: SafePath): BuilderEnd
        fun mimeType(value: SafePath): BuilderEnd
        fun move(value: SafePath): BuilderTo
        fun setFile(value: SafePath): BuilderEnd
        fun sizeFile(value: SafePath): BuilderEnd
        fun share(value: SafePath): BuilderEnd
        fun stream(value: SafePath): BuilderEnd
    }

    interface BuilderEnd {
        fun build(): HttpException
    }

    interface BuilderTo {
        fun to(value: SafePath): BuilderEnd
    }

    private class Builder(value: String) : BuilderStart, BuilderEnd, BuilderTo {
        private val builder = StringBuilder(value)

        override fun copy(value: SafePath): Builder =
            appendAction("copy", value)

        override fun children(value: SafePath): Builder =
            appendAction("children", value)

        override fun create(value: SafePath): Builder =
            appendAction("create", value)

        override fun delete(value: SafePath): Builder =
            appendAction("delete", value)

        override fun getFile(value: SafePath): Builder =
            appendAction("get file", value)

        override fun lastModified(value: SafePath): Builder =
            appendAction("get last modified", value)

        override fun mimeType(value: SafePath): Builder =
            appendAction("get mime type", value)

        override fun move(value: SafePath): Builder =
            appendAction("move", value)

        override fun setFile(value: SafePath): Builder =
            appendAction("set file", value)

        override fun sizeFile(value: SafePath): Builder =
            appendAction("get file size", value)

        override fun share(value: SafePath): Builder =
            appendAction("share", value)

        override fun stream(value: SafePath): Builder =
            appendAction("stream", value)

        override fun to(value: SafePath): Builder {
            builder.append(" to")
            return appendPath(value)
        }

        private fun appendAction(name: String, path: SafePath): Builder {
            builder.append(" ").append(name)
            return appendPath(path)
        }

        private fun appendPath(value: SafePath): Builder {
            builder.append(" ").append('"').append(value.absoluteString).append('"')
            return this
        }

        override fun build(): HttpException =
            HttpException(HttpStatus.BAD_REQUEST, builder.toString())
    }
}