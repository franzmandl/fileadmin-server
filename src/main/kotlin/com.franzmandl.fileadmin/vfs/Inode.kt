package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.dto.NewInode
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.filter.FilterResult
import com.franzmandl.fileadmin.resource.RequestCtx
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.attribute.FileTime

interface TreeInode {
    fun create(ctx: RequestCtx, isFile: Boolean)
    fun delete(ctx: RequestCtx)
    val isVirtual: Boolean
    fun move(ctx: RequestCtx, target: Inode1<*>)
    val parent: OptionalValue<Inode0>
    val path: SafePath
    val treeOperation: Operation
    val treePermission: Operation

    interface Operation {
        val canInodeDelete: Boolean
        val canInodeMove: Boolean
        val canInodeRename: Boolean
    }
}

interface HasChildren {
    val children: Set<SafePath>
}

interface ContentInode : HasChildren {
    val bytes: ByteArray get() = inputStream.use { it.readBytes() }
    override val children: Set<SafePath>
    val contentOperation: Operation
    val contentPermission: Operation
    fun copy(ctx: RequestCtx, target: Inode1<*>)
    val estimatedSizeOfDirectory: Int? get() = sizeOfDirectory
    val estimatedSizeOfFile: Long? get() = sizeOfFile
    val inputStream: InputStream
    val isDirectory: Boolean
    val isFile: Boolean
    val lastModified: FileTime?
    val lines: List<String> get() = inputStream.bufferedReader().useLines { it.toList() }
    val mimeType: String
    val outputStream: OutputStream
    val sizeOfDirectory: Int
    val sizeOfFile: Long
    fun setText(value: String) = outputStream.bufferedWriter().use { it.write(value) }
    fun share(ctx: RequestCtx, days: Int): String
    fun stream(requestHeaders: HttpHeaders, responseHeaders: HttpHeaders): ResponseEntity<ResourceRegion>
    val text: String get() = inputStream.bufferedReader().use { it.readText() }

    interface Operation {
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
}

interface Inode0 : ContentInode, TreeInode {
    companion object {
        val parentOfRoot = OptionalValue.Fail<Inode0>("Inode is root.")

        fun getChildrenAsText(inode: Inode0): String =
            getChildrenAsTextTo(StringBuilder(), inode).toString()

        fun <A : Appendable> getChildrenAsTextTo(buffer: A, inode: Inode0): A {
            for (child in inode.children) {
                buffer.appendLine(child.name)
            }
            return buffer
        }

        fun getNthAncestor(inode: Inode0, nth: Int): Inode0 {
            var ancestor: Inode0? = inode
            for (ignore in nth..inode.path.parts.lastIndex) {
                ancestor = (ancestor ?: break).parent.nullableValue
            }
            return ancestor ?: error("""Inode "${inode.path.absoluteString}" does not have a $nth ancestor.""")
        }

        fun getAncestors(inode: Inode0): List<Inode0> {
            val expectedAncestorsSize = inode.path.parts.size + 1
            val ancestors = ArrayList<Inode0>(expectedAncestorsSize)
            var mutableInode: Inode0? = inode
            while (mutableInode != null) {
                ancestors.add(0, mutableInode)
                mutableInode = mutableInode.parent.nullableValue
            }
            check(ancestors.size == expectedAncestorsSize) {
                """Inode "${inode.path.absoluteString}" has ancestors: """ + ancestors.joinToString { it.path.absoluteString }
            }
            return ancestors
        }

        fun getAncestorMap(inode: Inode0): Map<SafePath, Inode0> {
            val ancestors = mutableMapOf<SafePath, Inode0>()
            var mutableInode: Inode0? = inode
            while (mutableInode != null) {
                ancestors[mutableInode.path] = mutableInode
                mutableInode = mutableInode.parent.nullableValue
            }
            return ancestors
        }
    }
}

class Inode1<T : Inode0>(
    val config: Config,
    val inode0: T,
) : HasChildren {
    class Config(
        val errors: List<String>,
        val filter: FilterFileSystem?,
        val filterResult: FilterResult?,
        val isRunLast: Boolean,
        val isTask: Boolean,
        val nameCursorPosition: Int?,
        val newInodeTemplate: NewInode,
        val samePaths: Set<SafePath>,
        val stepchildren: Set<SafePath>,
    )

    override val children: Set<SafePath> get() = inode0.children + config.stepchildren

    override fun toString(): String =
        inode0.toString()
}