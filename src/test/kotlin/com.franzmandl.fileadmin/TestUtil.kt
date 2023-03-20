package com.franzmandl.fileadmin

import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.model.NewInode
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.NativeInode
import com.franzmandl.fileadmin.vfs.SafePath
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

object TestUtil {
    fun createNativeInode(string: String): NativeInode =
        NativeInode(
            Path.of(string),
            Inode.parentOfRoot,
            SafePath(
                when {
                    string.startsWith(File.separator) -> string
                    string.startsWith(".${File.separator}") -> string.substring(1)
                    string == "." -> File.separator
                    else -> "${File.separator}$string"
                }
            ),
            setOf(),
            NativeInodeConfig,
        )

    fun pathToUri(path: SafePath): String =
        stringToUri(path.toString())

    private fun stringToUri(string: String): String =
        string.split("/").joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()).replace("+", "%20") }

    private object NativeInodeConfig : Inode.Config {
        override val errors: List<String> = listOf()
        override val filter: FilterFileSystem? = null
        override var filterHighlightTags: Set<Tag>? = null
        override val isRunLast: Boolean = false
        override val isTask: Boolean = false
        override val nameCursorPosition: Int = 0
        override val newInodeTemplate: NewInode = NewInode(true, "")
    }
}