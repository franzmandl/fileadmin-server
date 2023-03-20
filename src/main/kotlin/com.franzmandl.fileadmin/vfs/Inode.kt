package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.model.NewInode
import com.franzmandl.fileadmin.resource.RequestCtx
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.attribute.FileTime

interface TreeInodeWithoutConfig {
    fun create(ctx: RequestCtx, isFile: Boolean)
    fun delete(ctx: RequestCtx)
    val isVirtual: Boolean
    fun move(ctx: RequestCtx, target: Inode)
    val parent: OptionalValue<InodeWithoutConfig>
    val path: SafePath
    val treeOperation: Inode.TreeOperation
    val treePermission: Inode.TreeOperation
}

interface TreeInode : TreeInodeWithoutConfig {
    val config: Inode.Config
}

interface ContentInode {
    val bytes: ByteArray get() = inputStream.use { it.readBytes() }
    val children: Set<SafePath>
    val contentOperation: Inode.ContentOperation
    val contentPermission: Inode.ContentOperation
    fun copy(ctx: RequestCtx, target: Inode)
    val inputStream: InputStream
    val isDirectory: Boolean
    val isFile: Boolean
    val lastModified: FileTime?
    val lines: List<String> get() = inputStream.bufferedReader().useLines { it.toList() }
    val mimeType: String
    val outputStream: OutputStream
    val sizeFile: Long
    fun setText(value: String) = outputStream.bufferedWriter().use { it.write(value) }
    fun share(ctx: RequestCtx, days: Int): String
    fun stream(requestHeaders: HttpHeaders, responseHeaders: HttpHeaders): ResponseEntity<ResourceRegion>
    val text: String get() = inputStream.bufferedReader().use { it.readText() }
}

interface InodeWithoutConfig : ContentInode, TreeInodeWithoutConfig

interface Inode : InodeWithoutConfig, TreeInode {
    companion object {
        val parentOfConfig = OptionalValue.Fail<InodeWithoutConfig>("Inode is config.")
        val parentOfRoot = OptionalValue.Fail<InodeWithoutConfig>("Inode is root.")

        fun getChildrenAsText(inode: Inode): String =
            getChildrenAsTextTo(StringBuilder(), inode).toString()

        fun <A : Appendable> getChildrenAsTextTo(buffer: A, inode: Inode): A {
            for (child in inode.children) {
                buffer.appendLine(child.name)
            }
            return buffer
        }
    }

    interface Config {
        val errors: List<String>
        val filter: FilterFileSystem?
        var filterHighlightTags: Set<Tag>?
        val isRunLast: Boolean
        val isTask: Boolean
        val nameCursorPosition: Int?
        val newInodeTemplate: NewInode
    }

    interface ContentOperation {
        /** Can inode act as a directory to add other inodes. */
        val canDirectoryAdd: Boolean
        val canDirectoryGet: Boolean
        val canFileGet: Boolean
        val canFileSet: Boolean
        val canFileStream: Boolean
        val canInodeCopy: Boolean
        val canInodeShare: Boolean
        val canInodeToDirectory: Boolean
        val canInodeToFile: Boolean
    }

    interface TreeOperation {
        val canInodeDelete: Boolean
        val canInodeMove: Boolean
        val canInodeRename: Boolean
    }
}