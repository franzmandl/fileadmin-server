package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.resource.RequestCtx
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.attribute.FileTime

class VirtualDirectory(
    override val config: Inode.Config,
    override val contentOperation: ContentOperation,
    override val children: Set<SafePath>,
    override val parent: OptionalValue<InodeWithoutConfig>,
    override val path: SafePath,
    override val treeOperation: TreeOperation,
) : Inode {
    override val contentPermission: Inode.ContentOperation = contentOperation
    override fun copy(ctx: RequestCtx, target: Inode) = throw HttpException.notSupported().copy(path).to(target.path).build()
    override fun create(ctx: RequestCtx, isFile: Boolean) = throw HttpException.notSupported().create(path).build()
    override fun delete(ctx: RequestCtx) = treeOperation.delete(ctx, this)
    override val inputStream: InputStream get() = throw HttpException.notSupported().getFile(path).build()
    override val isDirectory: Boolean = true
    override val isFile: Boolean = false
    override val isVirtual: Boolean = true
    override val lastModified: FileTime? = null
    override val mimeType: String get() = throw HttpException.notSupported().mimeType(path).build()
    override fun move(ctx: RequestCtx, target: Inode) = treeOperation.move(ctx, this, target)
    override val outputStream: OutputStream get() = throw HttpException.notSupported().setFile(path).build()
    override val sizeFile: Long get() = throw HttpException.notSupported().sizeFile(path).build()
    override fun share(ctx: RequestCtx, days: Int): String = throw HttpException.notSupported().share(path).build()
    override fun stream(requestHeaders: HttpHeaders, responseHeaders: HttpHeaders): ResponseEntity<ResourceRegion> = throw HttpException.notSupported().stream(path).build()
    override val treePermission: Inode.TreeOperation = treeOperation

    object ContentOperation : Inode.ContentOperation {
        override val canDirectoryAdd: Boolean = false
        override val canDirectoryGet: Boolean = true
        override val canFileGet: Boolean = false
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
        fun createFromFinder(finder: PathFinder, treeOperation: TreeOperation, children: Set<SafePath>): VirtualDirectory =
            VirtualDirectory(finder, ContentOperation, children, finder.parent, finder.destination, treeOperation)

        fun createFromFinderAndNames(finder: PathFinder, treeOperation: TreeOperation, names: Iterable<String>): VirtualDirectory =
            VirtualDirectory(finder, ContentOperation, names.mapTo(mutableSetOf()) { finder.destination.resolve(it) }, finder.parent, finder.destination, treeOperation)
    }
}