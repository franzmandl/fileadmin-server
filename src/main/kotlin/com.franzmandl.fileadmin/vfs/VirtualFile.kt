package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.resource.RequestCtx
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.attribute.FileTime

class VirtualFile(
    override val bytes: ByteArray,
    override val config: Inode.Config,
    override val contentOperation: ContentOperation,
    override val mimeType: String,
    override val parent: OptionalValue<InodeWithoutConfig>,
    override val path: SafePath,
    override val treeOperation: TreeOperation,
) : Inode {
    override val children: Set<SafePath> get() = throw HttpException.notSupported().children(path).build()
    override val contentPermission: Inode.ContentOperation = contentOperation
    override fun copy(ctx: RequestCtx, target: Inode) = throw HttpException.notSupported().copy(path).to(target.path).build()
    override fun create(ctx: RequestCtx, isFile: Boolean) = throw HttpException.notSupported().create(path).build()
    override fun delete(ctx: RequestCtx) = treeOperation.delete(ctx, this)
    override val inputStream: InputStream get() = ByteArrayInputStream(bytes)
    override val isDirectory: Boolean = false
    override val isFile: Boolean = true
    override val isVirtual: Boolean = true
    override val lastModified: FileTime? = null
    override val lines: List<String> get() = text.split("\n")
    override fun move(ctx: RequestCtx, target: Inode) = treeOperation.move(ctx, this, target)
    override val outputStream: OutputStream get() = throw HttpException.notSupported().setFile(path).build()
    override val sizeFile: Long = bytes.size.toLong()
    override fun share(ctx: RequestCtx, days: Int): String = throw HttpException.notSupported().share(path).build()
    override fun stream(requestHeaders: HttpHeaders, responseHeaders: HttpHeaders): ResponseEntity<ResourceRegion> = throw HttpException.notSupported().stream(path).build()
    override val text: String get() = String(bytes)
    override val treePermission: Inode.TreeOperation = treeOperation

    object ContentOperation : Inode.ContentOperation {
        override val canDirectoryAdd: Boolean = false
        override val canDirectoryGet: Boolean = false
        override val canFileGet: Boolean = true
        override val canFileSet: Boolean = false
        override val canFileStream: Boolean = false
        override val canInodeCopy: Boolean = false
        override val canInodeShare: Boolean = false
        override val canInodeToDirectory: Boolean = false
        override val canInodeToFile: Boolean = false
    }

    interface TreeOperation : Inode.TreeOperation {
        override val canInodeDelete: Boolean get() = false
        override val canInodeMove: Boolean get() = false
        override val canInodeRename: Boolean get() = false

        fun delete(ctx: RequestCtx, inode: Inode): Unit =
            throw HttpException.notSupported().delete(inode.path).build()

        fun move(ctx: RequestCtx, inode: Inode, target: Inode): Unit =
            throw HttpException.notSupported().move(inode.path).to(target.path).build()

        object Default : TreeOperation
    }

    companion object {
        fun createFromFinder(finder: PathFinder, treeOperation: TreeOperation, mimeType: String, bytes: ByteArray): VirtualFile =
            VirtualFile(bytes, finder, ContentOperation, mimeType, finder.parent, finder.destination, treeOperation)
    }
}