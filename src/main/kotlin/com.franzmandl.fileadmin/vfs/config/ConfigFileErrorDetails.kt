package com.franzmandl.fileadmin.vfs.config

data class ConfigFileErrorDetails(
    val lineNumber: Int,
    val nextName: String?,
) {
    companion object {
        fun create(exception: IllegalArgumentException, getText: () -> String): ConfigFileErrorDetails? {
            val message = exception.message ?: return null
            val offset = Regex("""^Unexpected JSON token at offset (\d+): """).find(message)?.groups?.get(1)?.value?.toIntOrNull() ?: return null
            val text = getText()
            return ConfigFileErrorDetails(
                lineNumber = text.subSequence(0, offset).count { it == '\n' },
                nextName = Regex(""""name": "(.*?)"""").find(text, offset)?.groups?.get(1)?.value,
            )
        }
    }

    override fun toString(): String {
        val builder = StringBuilder(" near line $lineNumber")
        if (nextName != null) {
            builder.append(""" near name "$nextName"""")
        }
        return builder.toString()
    }
}