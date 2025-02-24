package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.HttpException
import java.io.File
import java.nio.file.Path

object PathUtil {
    val parentLocalPath: Path = Path.of("..")
    val separator = if (File.separatorChar == '/') SlashSeparator else OtherSeparator(File.separatorChar)

    sealed interface Separator {
        val pattern: String
        fun replaceTo(string: String): String
        fun replaceFrom(string: String): String
        val char: Char
    }

    object SlashSeparator : Separator {
        override val pattern = "/"
        override fun replaceTo(string: String): String = string
        override fun replaceFrom(string: String): String = string
        override val char = '/'
    }

    class OtherSeparator(
        override val char: Char,
    ) : Separator {
        private val separatorString = char.toString()
        override val pattern = "/${Regex.escape(separatorString)}"
        override fun replaceTo(string: String): String = string.replace(char, '/')
        override fun replaceFrom(string: String): String = string.replace('/', char)
    }

    private val nameRegex = Regex("""^[^${separator.pattern}]+$""")

    fun validateName(name: String): String {
        if (!nameRegex.matches(name) || name == "." || name == "..") {
            throw HttpException.badRequest("""Illegal name: "$name" matches anti pattern.""")
        }
        return name
    }
}